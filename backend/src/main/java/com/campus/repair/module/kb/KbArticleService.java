package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.order.PartUsage;
import com.campus.repair.module.order.PartUsageMapper;
import com.campus.repair.module.order.RepairOrder;
import com.campus.repair.module.order.RepairOrderMapper;
import com.campus.repair.module.order.statemachine.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库：Markdown 文章的上传、浏览、检索语料同步，以及 R3 的自助事件/案例沉淀。
 *
 * <p>状态：0 停用 / 1 启用 / 2 草稿。草稿只对管理端可见，不进向量库；
 * 发布时才切片写入 Chroma。</p>
 */
@Service
public class KbArticleService {

    private static final Logger log = LoggerFactory.getLogger(KbArticleService.class);

    /** Markdown 上传上限，避免误传大文件 */
    private static final long MAX_MD_SIZE = 1024 * 1024L;

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_DRAFT = 2;

    private final KbArticleMapper kbArticleMapper;
    private final KbEventMapper kbEventMapper;
    private final KbVectorIndexService vectorIndex;
    private final RepairOrderMapper orderMapper;
    private final PartUsageMapper partMapper;
    private final UserMapper userMapper;

    public KbArticleService(KbArticleMapper kbArticleMapper,
                            KbEventMapper kbEventMapper,
                            KbVectorIndexService vectorIndex,
                            RepairOrderMapper orderMapper,
                            PartUsageMapper partMapper,
                            UserMapper userMapper) {
        this.kbArticleMapper = kbArticleMapper;
        this.kbEventMapper = kbEventMapper;
        this.vectorIndex = vectorIndex;
        this.orderMapper = orderMapper;
        this.partMapper = partMapper;
        this.userMapper = userMapper;
    }

    /* ==================== 浏览 ==================== */

    /** 小程序/登录用户：只返回启用文章；列表不带全文，避免列表页背大字段 */
    public PageVO<KbArticle> pagePublished(String keyword, long page, long pageSize) {
        Page<KbArticle> p = kbArticleMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<KbArticle>()
                        .eq(KbArticle::getStatus, STATUS_PUBLISHED)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(KbArticle::getTitle, keyword)
                                .or().like(KbArticle::getContent, keyword))
                        .orderByDesc(KbArticle::getUpdatedAt));
        p.getRecords().forEach(a -> a.setContent(null));
        // 搜索无结果：只记关键词，不阻断列表
        if (StringUtils.hasText(keyword) && p.getTotal() == 0) {
            recordSearchMiss(keyword);
        }
        return new PageVO<>(p.getRecords(), p.getTotal());
    }

    /** 管理端：含停用与草稿，可按状态筛选 */
    public PageVO<KbArticle> pageAdmin(String keyword, Integer status, long page, long pageSize) {
        Page<KbArticle> p = kbArticleMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<KbArticle>()
                        .eq(status != null, KbArticle::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(KbArticle::getTitle, keyword)
                                .or().like(KbArticle::getContent, keyword))
                        .orderByDesc(KbArticle::getUpdatedAt));
        return new PageVO<>(p.getRecords(), p.getTotal());
    }

    /** 详情：停用/草稿仅管理员可见；非管理员访问才累加浏览量 */
    public KbArticle detail(Long id, boolean admin) {
        KbArticle a = kbArticleMapper.selectById(id);
        if (a == null || (!admin && !isPublished(a))) {
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

    /* ==================== 维护 ==================== */

    /** 只允许改标题与状态；正文由上传的 Markdown 或草稿编辑决定 */
    public void update(Long id, KbReq req) {
        KbArticle exists = kbArticleMapper.selectById(id);
        if (exists == null) {
            throw BusinessException.notFound("文章不存在");
        }
        if (req.status() != null && req.status() == STATUS_DRAFT) {
            throw BusinessException.param("草稿状态只能由案例沉淀生成，不能手工设置");
        }
        KbArticle a = new KbArticle();
        a.setId(id);
        a.setTitle(req.title());
        if (req.status() != null) {
            a.setStatus(req.status());
        }
        a.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.updateById(a);
        // 停用会删除向量块，启用会重新写入，所以同步一次
        syncQuietly(kbArticleMapper.selectById(id));
    }

    /** 删除文章，同时清掉向量块；事件保留用于历史统计 */
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
     * 标题取文件名，向量库不可用时文章仍会保存并返回 warning。
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

        MarkdownDocParser.Parsed parsed = MarkdownDocParser.parse(raw);
        if (!StringUtils.hasText(parsed.content())) {
            throw BusinessException.param("文件内容为空");
        }

        KbArticle article = new KbArticle();
        article.setTitle(MarkdownDocParser.titleFromFileName(fileName));
        article.setContent(parsed.content());
        article.setStatus(STATUS_PUBLISHED);
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
        return new KbImportResult(article.getId(), article.getTitle(), chunks, indexed, warning);
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

    /* ==================== R3：自助事件 ==================== */

    /** 教程页「已解决 / 仍未解决去报修」 */
    public void recordEvent(Long articleId, String eventType) {
        if (!"SOLVED".equals(eventType) && !"UNSOLVED".equals(eventType)) {
            throw BusinessException.param("事件类型只能是 SOLVED 或 UNSOLVED");
        }
        KbArticle article = kbArticleMapper.selectById(articleId);
        if (article == null || !isPublished(article)) {
            throw BusinessException.notFound("文章不存在或未发布");
        }
        Long userId = AuthContext.userId();
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        // 同一用户同一文章 24 小时内只记一次反馈（已解决/未解决互斥），避免刷数
        long recent = kbEventMapper.countRecent(articleId, userId, "SOLVED", since)
                + kbEventMapper.countRecent(articleId, userId, "UNSOLVED", since);
        if (recent > 0) {
            return;
        }
        KbEvent e = new KbEvent();
        e.setArticleId(articleId);
        e.setUserId(userId);
        e.setEventType(eventType);
        e.setCreatedAt(LocalDateTime.now());
        kbEventMapper.insert(e);
    }

    /** 搜索无结果：只记关键词；写入失败不影响列表 */
    private void recordSearchMiss(String keyword) {
        try {
            String kw = keyword.trim();
            if (kw.isEmpty()) {
                return;
            }
            if (kw.length() > 64) {
                kw = kw.substring(0, 64);
            }
            Long userId = AuthContext.userId();
            LocalDateTime since = LocalDateTime.now().minusHours(24);
            if (kbEventMapper.countRecentSearchMiss(userId, kw, since) > 0) {
                return;
            }
            KbEvent e = new KbEvent();
            e.setUserId(userId);
            e.setEventType("SEARCH_MISS");
            e.setKeyword(kw);
            e.setCreatedAt(LocalDateTime.now());
            kbEventMapper.insert(e);
        } catch (Exception e) {
            log.warn("[KB] 记录搜索无结果失败：{}", e.getMessage());
        }
    }

    /** 知识库自助统计（R3） */
    public KbStatsVO stats(int days) {
        int window = Math.max(1, Math.min(days, 180));
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        long solved = 0;
        long unsolved = 0;
        long searchMiss = 0;
        for (Map<String, Object> row : kbEventMapper.countByType(from, to)) {
            String type = String.valueOf(row.get("eventType"));
            long total = number(row.get("total"));
            switch (type) {
                case "SOLVED" -> solved = total;
                case "UNSOLVED" -> unsolved = total;
                case "SEARCH_MISS" -> searchMiss = total;
                default -> { }
            }
        }

        Map<Long, long[]> byArticle = new HashMap<>();
        for (Map<String, Object> row : kbEventMapper.countByArticle(from, to)) {
            Long id = ((Number) row.get("articleId")).longValue();
            long[] v = byArticle.computeIfAbsent(id, k -> new long[2]);
            if ("SOLVED".equals(String.valueOf(row.get("eventType")))) {
                v[0] = number(row.get("total"));
            } else if ("UNSOLVED".equals(String.valueOf(row.get("eventType")))) {
                v[1] = number(row.get("total"));
            }
        }

        List<KbArticle> published = kbArticleMapper.selectList(new LambdaQueryWrapper<KbArticle>()
                .eq(KbArticle::getStatus, STATUS_PUBLISHED)
                .select(KbArticle::getId, KbArticle::getTitle, KbArticle::getViewCount)
                .orderByDesc(KbArticle::getViewCount)
                .last("LIMIT 10"));
        List<KbStatsVO.ArticleStat> topViewed = published.stream()
                .map(a -> articleStat(a, byArticle.get(a.getId())))
                .toList();

        List<KbStatsVO.ArticleStat> topUnsolved = byArticle.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted(Comparator.comparingLong((Map.Entry<Long, long[]> e) -> e.getValue()[1]).reversed())
                .limit(10)
                .map(e -> {
                    KbArticle a = kbArticleMapper.selectById(e.getKey());
                    return a == null ? null : articleStat(a, e.getValue());
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        List<KbStatsVO.KeywordStat> keywords = kbEventMapper.topSearchMiss(from, to, 20).stream()
                .map(row -> new KbStatsVO.KeywordStat(
                        String.valueOf(row.get("keyword")), number(row.get("total"))))
                .toList();

        Double rate = solved + unsolved == 0 ? null
                : Math.round(solved * 10000.0 / (solved + unsolved)) / 10000.0;
        return new KbStatsVO(solved, unsolved, searchMiss, rate, topViewed, topUnsolved, keywords);
    }

    /* ==================== R3：案例沉淀 ==================== */

    /** 完工单 → 知识库草稿（一单一次；草稿不进向量库） */
    @Transactional
    public KbArticle fromOrder(Long orderId) {
        RepairOrder o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BusinessException.notFound("工单不存在");
        }
        if (o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.EVALUATED) {
            throw BusinessException.param("只有已完成/已评价的工单可以沉淀为知识库");
        }
        KbArticle existing = kbArticleMapper.selectOne(new LambdaQueryWrapper<KbArticle>()
                .eq(KbArticle::getSourceOrderId, orderId));
        if (existing != null) {
            return existing;
        }

        String title = buildDraftTitle(o);
        String content = buildDraftContent(o);
        KbArticle article = new KbArticle();
        article.setTitle(title);
        article.setContent(content);
        article.setStatus(STATUS_DRAFT);
        article.setSourceOrderId(orderId);
        article.setViewCount(0);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.insert(article);
        log.info("[KB] 工单 {} 沉淀为知识库草稿 #{}", o.getOrderNo(), article.getId());
        return article;
    }

    /** 草稿编辑：只有 status=2 允许改正文 */
    public void updateDraft(Long id, KbDraftReq req) {
        KbArticle a = kbArticleMapper.selectById(id);
        if (a == null) {
            throw BusinessException.notFound("文章不存在");
        }
        if (a.getStatus() == null || a.getStatus() != STATUS_DRAFT) {
            throw BusinessException.param("只有草稿可以编辑正文");
        }
        if (!StringUtils.hasText(req.content())) {
            throw BusinessException.param("正文不能为空");
        }
        KbArticle update = new KbArticle();
        update.setId(id);
        if (StringUtils.hasText(req.title())) {
            update.setTitle(req.title().trim());
        }
        update.setContent(req.content());
        update.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.updateById(update);
    }

    /** 草稿发布：置为启用并切片写入向量库 */
    public KbImportResult publish(Long id) {
        KbArticle a = kbArticleMapper.selectById(id);
        if (a == null) {
            throw BusinessException.notFound("文章不存在");
        }
        if (a.getStatus() == null || a.getStatus() != STATUS_DRAFT) {
            throw BusinessException.param("只有草稿可以发布");
        }
        KbArticle update = new KbArticle();
        update.setId(id);
        update.setStatus(STATUS_PUBLISHED);
        update.setUpdatedAt(LocalDateTime.now());
        kbArticleMapper.updateById(update);

        KbArticle published = kbArticleMapper.selectById(id);
        int chunks = 0;
        boolean indexed = false;
        String warning = null;
        if (!vectorIndex.available()) {
            warning = "已发布，但向量检索未启用，未写入索引";
        } else {
            try {
                chunks = vectorIndex.index(published);
                indexed = true;
            } catch (Exception e) {
                warning = "已发布，但向量索引失败：" + e.getMessage();
                log.warn("[KB] 发布文章 #{} 索引失败：{}", id, e.getMessage());
            }
        }
        return new KbImportResult(id, published.getTitle(), chunks, indexed, warning);
    }

    /* ==================== 内部 ==================== */

    private boolean isPublished(KbArticle a) {
        return a.getStatus() != null && a.getStatus() == STATUS_PUBLISHED;
    }

    private KbStatsVO.ArticleStat articleStat(KbArticle a, long[] counts) {
        long solved = counts == null ? 0 : counts[0];
        long unsolved = counts == null ? 0 : counts[1];
        Double rate = solved + unsolved == 0 ? null
                : Math.round(unsolved * 10000.0 / (solved + unsolved)) / 10000.0;
        return new KbStatsVO.ArticleStat(a.getId(), a.getTitle(),
                a.getViewCount() == null ? 0 : a.getViewCount(), solved, unsolved, rate);
    }

    private String buildDraftTitle(RepairOrder o) {
        String detail = o.getLocationText() == null ? "" : o.getLocationText();
        int dot = detail.indexOf('·');
        if (dot >= 0) {
            detail = detail.substring(dot + 1);
        }
        // 草稿默认脱敏：数字（房间号/门牌号）统一替换，发布前由管理员确认
        detail = detail.replaceAll("\\d+", "···").trim();
        if (detail.length() > 18) {
            detail = detail.substring(0, 18) + "…";
        }
        if (detail.isEmpty()) {
            detail = "校园设施";
        }
        return o.getFaultType() + " · " + detail + " · "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String buildDraftContent(RepairOrder o) {
        String workerName = "未指派";
        if (o.getWorkerId() != null) {
            SysUser worker = userMapper.selectById(o.getWorkerId());
            if (worker != null) {
                workerName = worker.getName();
            }
        }
        List<PartUsage> parts = partMapper.selectList(new LambdaQueryWrapper<PartUsage>()
                .eq(PartUsage::getOrderId, o.getId()));
        String partText = parts.isEmpty() ? "无" : parts.stream()
                .map(p -> "- " + p.getPartName() + " × " + p.getQuantity())
                .collect(Collectors.joining("\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(buildDraftTitle(o)).append("\n\n");
        sb.append("## 现象\n").append(nullToEmpty(o.getDescription())).append("\n\n");
        sb.append("## 处理过程\n")
                .append(StringUtils.hasText(o.getFinishSummary()) ? o.getFinishSummary() : "未填写，发布前请补充")
                .append("\n\n");
        sb.append("## 更换配件\n").append(partText).append("\n\n");
        sb.append("## 来源\n");
        sb.append("- 工单号：").append(o.getOrderNo()).append("\n");
        sb.append("- 维修工：").append(workerName).append("\n");
        sb.append("- 完工时间：").append(o.getCompletedAt() == null ? "—" : o.getCompletedAt()).append("\n");
        sb.append("- 说明：由完工记录自动生成，发布前请确认无个人信息。\n");
        return sb.toString();
    }

    /** 向量库不可用时不影响文章本身的增删改，只记日志 */
    private void syncQuietly(KbArticle article) {
        if (article == null) {
            return;
        }
        try {
            if (isPublished(article)) {
                vectorIndex.index(article);
            } else {
                vectorIndex.remove(article.getId());
            }
        } catch (Exception e) {
            log.warn("[KB] 文章 #{} 向量同步失败：{}", article.getId(), e.getMessage());
        }
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
