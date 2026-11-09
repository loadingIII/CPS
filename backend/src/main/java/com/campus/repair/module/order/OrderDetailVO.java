package com.campus.repair.module.order;

import com.campus.repair.module.order.statemachine.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单详情（含图片分组、评价、双方联系方式）。
 */
@Data
public class OrderDetailVO {

    private Long id;
    private String orderNo;
    private Long reporterId;
    private Long workerId;
    private String campus;
    private String campusName;
    private String locationText;
    /** 导航用坐标（工单地图选点优先，否则取校区默认中心坐标） */
    private Double locationLng;
    private Double locationLat;
    /** 报修时的地图选点 POI 名称（可空） */
    private String mapName;
    private String faultType;
    private Urgency urgency;
    private String description;
    private OrderStatus status;
    private Boolean isOverdue;

    private String reporterName;
    private String reporterPhone;
    private String workerName;
    private String workerPhone;

    private LocalDateTime submittedAt;
    private LocalDateTime auditedAt;
    private LocalDateTime assignedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime ratedAt;

    private List<String> reportImages;
    private List<String> finishImages;

    private Evaluation evaluation;
}
