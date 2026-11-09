package com.campus.repair.module.order;

import java.util.List;

/**
 * 管理工作台统计视图：状态为全量累计，趋势与故障类型使用固定近期开窗。
 */
public record OrderStatisticsVO(
        long total,
        List<StatusCount> statuses,
        List<DailyCount> daily,
        List<FaultTypeCount> faultTypes
) {
    public record StatusCount(String status, long count) {}
    public record DailyCount(String day, long count) {}
    public record FaultTypeCount(String faultType, long count) {}
}
