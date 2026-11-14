package com.campus.repair.module.stats.dto;

import lombok.Data;

/**
 * 按日工单量与按时完成率（R2-F9-1）。
 */
@Data
public class OntimePointVO {

    private String day;
    /** 当日提交量 */
    private long submitted;
    /** 当日完工量（分母） */
    private long completed;
    /** 按时完工量 */
    private long onTime;
    /** 按时完成率 = onTime / completed，0~1；无完工样本时为 null */
    private Double onTimeRate;
}
