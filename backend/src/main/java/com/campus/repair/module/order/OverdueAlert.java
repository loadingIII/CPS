package com.campus.repair.module.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 超时预警事件（R2-F8-3）：与工单 is_overdue 标记位职责分离。
 * stage = ACCEPT 接单超时 / PROCESS 处理超时；level 随同一单重复超时递增。
 */
@Data
@TableName("overdue_alert")
public class OverdueAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String stage;

    private Integer level;

    private Boolean handled;

    private LocalDateTime createdAt;
}
