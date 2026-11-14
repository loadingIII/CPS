package com.campus.repair.module.order;

import java.util.List;

/**
 * 管理工作台统计视图：状态为全量累计，趋势与故障类型使用固定近期开窗。
 *
 * @param pendingPool 真正待派单的数量（PENDING 且 worker_id 为空），与派单台 POOL 队列口径一致
 */
public record OrderStatisticsVO(
        long total,
        long pendingPool,
        List<StatusCount> statuses,
        List<DailyCount> daily,
        List<FaultTypeCount> faultTypes
) {
    public record StatusCount(String status, long count) {}
    public record DailyCount(String day, long count) {}
    public record FaultTypeCount(String faultType, long count) {}
}
