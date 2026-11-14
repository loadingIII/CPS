package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修知识库文章（R2-F10-1/F10-2）：仅图文浏览与搜索，不含 RAG。
 */
@Data
@TableName("kb_article")
public class KbArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String faultType;

    private String content;

    private Integer viewCount;

    /** 1 启用 / 0 停用 */
    private Boolean status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
