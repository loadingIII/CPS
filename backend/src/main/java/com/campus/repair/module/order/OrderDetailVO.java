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

    /** 完工维修说明（故障原因/处理方式） */
    private String finishSummary;

    /** 派单调度信息 */
    private DispatchState dispatchState;
    private DispatchSource dispatchSource;
    private LocalDateTime grabOpenAt;
    private LocalDateTime assignExpiresAt;
    private LocalDateTime dueAt;
    private Integer dispatchCount;
    private Integer rejectCount;

    private List<String> reportImages;
    private List<String> finishImages;
    /** 配件消耗（R2-F5-5） */
    private List<PartUsage> parts;
    /** 合并后的主单 id（R2-F3-3） */
    private Long mainOrderId;
    /** 主单工单号，前端直接展示，避免让用户看数字 id */
    private String mainOrderNo;

    private Evaluation evaluation;
}
