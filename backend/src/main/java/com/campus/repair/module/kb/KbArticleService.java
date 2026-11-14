package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.module.agent.KeywordAgentFallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 知识库（R2-F10-1/F10-2）：分类浏览、关键词搜索、详情与浏览量。
 * 文章增删改会同步到向量库（{@link KbVectorIndexService}），供智能助手检索。
 */
@Service
public class KbArticleService {

    private static final Logger log = LoggerFactory.getLogger(KbArticleService.class);

    /** Markdown 上传上限，避免误传大文件 */
    private static final long MAX_MD_SIZE = 1024 * 1024L;

    private final KbArticleMapper kbArticleMapper;
    private final KbVectorIndexService vectorIndex;
    private final KeywordAgentFallback faultTypeGuesser;

    public KbArticleService(KbArticleMapper kbArticleMapper,
                            KbVectorIndexService vectorIndex,
                            KeywordAgentFallback faultTypeGuesser) {
        this.kbArticleMapper = kbArticleMapper;
        this.vectorIndex = vectorIndex;
        this.faultTypeGuesser = faultTypeGuesser;
    }

    /** 小程序/登录用户：只返回启用文章；列表不带全文，避免列表页背大字段 */
    public PageVO<KbArticle> pagePublished(String faultType, String keyword, long page, long pageSize) {
        Page<KbArticle> p = kbArticleMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<KbArticle>()
                        .eq(KbArticle::getStatus, true)
                        .eq(StringUtils.hasText(faultType), KbArticle::getFaultType, faultType)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(KbArticle::getTitle, keyword)
                                .or().like(KbArticle::getContent, keyword))
                        .orderByDesc(KbArticle::getUpdatedAt));
        p.getRecords().forEach(a -> a.setContent(null));
        return new PageVO<>(p.getRecords(), p.getTotal());
    }

    /** 管理端：含停用文章 */
    public PageVO<KbArticle> pageAdmin(String faultType, String keyword, long page, long pageSize) {
        Page<KbArticle> p = kbArticleMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<KbArticle>()
                        .eq(StringUtils.hasText(faultType), KbArticle::getFaultType, faultType)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(KbArticle::getTitle, keyword)
                                .or().like(KbArticle::getContent, keyword))
                        .orderByDesc(KbArticle::getUpdatedAt));
        return new PageVO<>(p.getRecords(), p.getTotal());
    }

    /** 详情：停用文章仅管理员可见 */
    public KbArticle detail(Long id, boolean admin) {
        KbArticle a = kbArticleMapper.selectById(id);
        if (a == null || (!admin && !Boolean.TRUE.equals(a.getStatus()))) {
            throw BusinessException.notFound("文章不存在");
        }
        if (!admin) {
            kbArticleMapper.update(null, new LambdaUpdateWrapper<KbArticle>()
                    .eq(KbArticle::getId, id)
                    .setSql("view_count = view_count + 1"));
            a.setViewCount((a.getViewCount() == null ? 0 : a.getViewCount()) + 1);
        }
        return a;
    }

    public Long create(KbReq req) {
        KbArticle a = new KbArticle();
        apply(a, req);
        a.setViewCount(0);
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.insert(a);
        syncQuietly(a);
        return a.getId();
    }

    public void update(Long id, KbReq req) {
        KbArticle exists = kbArticleMapper.selectById(id);
        if (exists == null) {
            throw BusinessException.notFound("文章不存在");
        }
        KbArticle a = new KbArticle();
        a.setId(id);
        apply(a, req);
        a.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.updateById(a);
        // 重新查一次，保证同步用的是完整字段
        syncQuietly(kbArticleMapper.selectById(id));
    }

    /** 删除文章，同时清掉向量块 */
    public void delete(Long id) {
        KbArticle exists = kbArticleMapper.selectById(id);
        if (exists == null) {
            throw BusinessException.notFound("文章不存在");
        }
        kbArticleMapper.deleteById(id);
        try {
            vectorIndex.remove(id);
        } catch (Exception e) {
            log.warn("[KB] 删除文章 #{} 的向量块失败：{}", id, e.getMessage());
        }
    }

    /**
     * 上传 Markdown：解析 → 建文章 → 切片 → 向量化入库，一步完成。
     * 向量库不可用时文章仍会保存，结果里给出 warning。
     */
    public KbImportResult importMarkdown(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.param("请选择 Markdown 文件");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = fileName.toLowerCase();
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
            throw BusinessException.param("只支持 .md / .markdown 文件");
        }
        if (file.getSize() > MAX_MD_SIZE) {
            throw BusinessException.param("文件不要超过 1MB");
        }

        String raw;
        try {
            raw = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw BusinessException.param("读取文件失败：" + e.getMessage());
        }

        MarkdownDocParser.Parsed parsed = MarkdownDocParser.parse(raw, fileName);
        if (!StringUtils.hasText(parsed.content())) {
            throw BusinessException.param("文件内容为空");
        }

        String faultType = parsed.faultType();
        if (!StringUtils.hasText(faultType)) {
            // 没写 front-matter 时按标题 + 正文关键词猜一个，猜不到归「其他」
            faultType = faultTypeGuesser.classify(parsed.title() + "\n" + parsed.content());
        }
        if (!StringUtils.hasText(faultType)) {
            faultType = "其他";
        }

        KbArticle article = new KbArticle();
        article.setTitle(parsed.title());
        article.setFaultType(faultType);
        article.setContent(parsed.content());
        article.setStatus(Boolean.TRUE);
        article.setViewCount(0);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.insert(article);

        int chunks = 0;
        boolean indexed = false;
        String warning = null;
        if (!vectorIndex.available()) {
            warning = "文章已保存，但向量检索未启用，未写入索引";
        } else {
            try {
                chunks = vectorIndex.index(article);
                indexed = true;
            } catch (Exception e) {
                warning = "文章已保存，但向量索引失败：" + e.getMessage();
                log.warn("[KB] 导入文章 #{} 索引失败：{}", article.getId(), e.getMessage());
            }
        }
        log.info("[KB] 导入《{}》#{}：{} 块，indexed={}", article.getTitle(), article.getId(), chunks, indexed);
        return new KbImportResult(article.getId(), article.getTitle(), faultType, chunks, indexed, warning);
    }

    /** 全量重建向量索引（管理端「重建索引」按钮） */
    public KbVectorIndexService.SyncReport reindex() {
        if (!vectorIndex.available()) {
            throw BusinessException.conflict("向量检索未启用，请先在 .env 配置 AI_EMBEDDING_* 并启动 Chroma");
        }
        try {
            vectorIndex.checkConnection();
        } catch (Exception e) {
            throw BusinessException.conflict("连不上 Chroma 或集合创建失败：" + e.getMessage());
        }
        return vectorIndex.reindexAll();
    }

    /** 向量库不可用时不影响文章本身的增删改，只记日志 */
    private void syncQuietly(KbArticle article) {
        if (article == null) {
            return;
        }
        try {
            vectorIndex.index(article);
        } catch (Exception e) {
            log.warn("[KB] 文章 #{} 向量同步失败：{}", article.getId(), e.getMessage());
        }
    }

    private void apply(KbArticle a, KbReq req) {
        a.setTitle(req.title());
        a.setFaultType(req.faultType());
        a.setContent(req.content());
        a.setStatus(req.status() == null ? Boolean.TRUE : req.status());
    }
}
