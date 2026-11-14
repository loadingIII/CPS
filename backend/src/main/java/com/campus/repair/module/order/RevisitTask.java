package com.campus.repair.module.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 差评回访任务（R2-F7-3）：评价 ≤2 星自动生成，管理员处理后关闭。
 */
@Data
@TableName("revisit_task")
public class RevisitTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long workerId;

    private Integer rating;

    /** PENDING 待回访 / HANDLED 已回访 / CLOSED 已关闭 */
    private String status;

    private Long handledBy;

    private LocalDateTime handledAt;

    private String remark;

    private LocalDateTime createdAt;
}
