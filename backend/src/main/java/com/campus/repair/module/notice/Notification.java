package com.campus.repair.module.notice;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（R2-F6-3）：状态变更、派单、催单、超时预警、回访任务统一落这张表。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiverId;

    /** 业务类型：AUDIT_PASS/AUDIT_REJECT/ASSIGN/ACCEPT/REJECT/FINISH/RATE/URGE/OVERDUE/REVISIT/MERGE */
    private String type;

    private String content;

    private Boolean isRead;

    /** 关联工单 id，前端据此跳转详情 */
    private Long bizId;

    private LocalDateTime createdAt;
}
