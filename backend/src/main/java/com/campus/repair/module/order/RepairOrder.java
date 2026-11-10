package com.campus.repair.module.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.repair.module.order.statemachine.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long reporterId;

    private Long workerId;

    /** 校区枚举 code（Campus.name()）：位置模型中唯一受控的一层 */
    private String campus;

    /** 完整位置串：校区名 · 报修人自由填写的详细位置 */
    private String locationText;

    /** 地图选点补充定位（DB-02，可空；未选点时导航回退到校区中心坐标） */
    private String mapName;

    private Double mapLng;

    private Double mapLat;

    private String faultType;

    private Urgency urgency;

    private String description;

    private OrderStatus status;

    private Boolean isOverdue;

    private Long mainOrderId;

    /** 乐观锁版本号 */
    private Integer version;

    /** 完工维修说明（故障原因/处理方式） */
    private String finishSummary;

    /** 最近一次派单来源：MANUAL 人工 / GRAB 抢单 / AUTO 自动 */
    private DispatchSource dispatchSource;

    /** 抢单池开放时间（紧急单=审核时，普通单=延迟开放） */
    private LocalDateTime grabOpenAt;

    /** 本次派单的接单截止时间（超时自动回收） */
    private LocalDateTime assignExpiresAt;

    /** 处理截止时间（接单/抢单时按 SLA 写入） */
    private LocalDateTime dueAt;

    /** 累计派单/抢单次数（识别难派单） */
    private Integer dispatchCount;

    /** 累计被拒/超时回收次数 */
    private Integer rejectCount;

    private LocalDateTime submittedAt;

    private LocalDateTime auditedAt;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime completedAt;

    private LocalDateTime ratedAt;
}
