package com.campus.repair.module.order;

import com.campus.repair.module.order.statemachine.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    private Long workerId;
    private String workerName;
    private LocalDateTime submittedAt;

    /** 派单子状态：POOL 待派单 / OFFERED 已派待接 / TAKEN 已承接 / NONE */
    private DispatchState dispatchState;
    private LocalDateTime grabOpenAt;
    private LocalDateTime assignExpiresAt;
    private Integer dispatchCount;
    private Integer rejectCount;

    /** 抢单大厅专用：匹配分与推荐理由 */
    private Double matchScore;
    private List<String> matchReasons;
}
