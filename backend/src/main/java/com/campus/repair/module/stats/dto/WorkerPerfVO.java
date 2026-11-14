package com.campus.repair.module.stats.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 维修工绩效行（R2-F9-3）：完成量 / 按时率 / 满意度。
 * 完工数少于 5 单标记样本不足，不参与正式排名。
 */
@Data
public class WorkerPerfVO {

    private Long workerId;
    private String name;
    private long done;
    private long onTime;
    private Double onTimeRate;
    private Double avgRating;
    private BigDecimal score;
    private boolean sampleEnough;
}
