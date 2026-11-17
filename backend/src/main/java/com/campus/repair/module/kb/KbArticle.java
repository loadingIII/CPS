package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修知识库文章：Markdown 正文，标题默认取上传文件名。
 * 同时作为智能助手的检索语料（切片后写入 Chroma）。
 */
@Data
@TableName("kb_article")
public class KbArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Integer viewCount;

    /** 0 停用 / 1 启用 / 2 草稿（草稿仅管理端可见，不进向量库） */
    private Integer status;

    /** 由哪张工单沉淀而来（逻辑关联，一单只沉淀一次） */
    private Long sourceOrderId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
