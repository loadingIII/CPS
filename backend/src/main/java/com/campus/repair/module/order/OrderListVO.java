package com.campus.repair.module.order;

import com.campus.repair.module.order.statemachine.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表行。
 */
@Data
public class OrderListVO {

    private Long id;
    private String orderNo;
    private String locationText;
    private String faultType;
    private Urgency urgency;
    private OrderStatus status;
    private Boolean isOverdue;
    private String description;
    private String reporterName;
    private String workerName;
    private LocalDateTime submittedAt;
}
