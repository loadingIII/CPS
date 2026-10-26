package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 维修工档案（1:1 扩展 sys_user，主键即用户 id）。
 */
@Data
@TableName("worker_profile")
public class WorkerProfile {

    @TableId
    private Long userId;

    private Boolean onDuty;

    /** 负载上限（在办工单数） */
    private Integer loadLimit;

    /** 综合评分 1.00~5.00 */
    private BigDecimal score;
}
