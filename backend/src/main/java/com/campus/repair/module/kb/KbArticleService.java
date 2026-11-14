package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 知识库（R2-F10-1/F10-2）：分类浏览、关键词搜索、详情与浏览量。
 * 只做图文，不做向量检索与智能推荐（R3）。
 */
@Service
public class KbArticleService {

    private final KbArticleMapper kbArticleMapper;

    public KbArticleService(KbArticleMapper kbArticleMapper) {
        this.kbArticleMapper = kbArticleMapper;
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
    }

    private void apply(KbArticle a, KbReq req) {
        a.setTitle(req.title());
        a.setFaultType(req.faultType());
        a.setContent(req.content());
        a.setStatus(req.status() == null ? Boolean.TRUE : req.status());
    }
}
