package com.campus.repair.module.order.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 超时预警列表行（R2-F8）。
 */
@Data
public class AlertVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String locationText;
    private String faultType;
    private String urgency;
    private String status;
    /** ACCEPT 接单超时 / PROCESS 处理超时 */
    private String stage;
    private Integer level;
    private Boolean handled;
    private LocalDateTime createdAt;
}
