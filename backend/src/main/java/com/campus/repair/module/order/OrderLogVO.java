package com.campus.repair.module.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 时间线视图（order_log + 操作人姓名）。
 */
@Data
public class OrderLogVO {

    private String action;
    private String operatorName;
    private String operatorRole;
    private String remark;
    private LocalDateTime createdAt;
}
