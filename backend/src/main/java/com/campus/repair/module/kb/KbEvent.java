package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库自助事件：SOLVED / UNSOLVED / SEARCH_MISS。
 *
 * <p>只用于统计，不参与权限判断；写入失败不能阻断阅读或报修。</p>
 */
@Data
@TableName("kb_event")
public class KbEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SEARCH_MISS 时为空 */
    private Long articleId;

    private Long userId;

    private String eventType;

    /** SEARCH_MISS 的搜索词 */
    private String keyword;

    private LocalDateTime createdAt;
}
