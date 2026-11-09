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

    private LocalDateTime submittedAt;

    private LocalDateTime auditedAt;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime completedAt;

    private LocalDateTime ratedAt;
}
