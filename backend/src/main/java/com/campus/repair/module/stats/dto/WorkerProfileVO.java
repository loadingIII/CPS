package com.campus.repair.module.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 维修工画像（R3）：近 90 天完工、按时、满意度、返修率与当前负载。
 */
public record WorkerProfileVO(Long id,
                              String name,
                              String jobNo,
                              String phone,
                              Boolean onDuty,
                              Integer loadLimit,
                              Integer currentLoad,
                              BigDecimal score,
                              List<String> skills,
                              List<String> campuses,
                              long done,
                              long onTime,
                              Double onTimeRate,
                              Double avgRating,
                              Double repairRate,
                              Double avgHours,
                              long overdueRecycles,
                              boolean sampleEnough,
                              List<RecentOrder> recentOrders) {

    public record RecentOrder(String orderNo,
                              String faultType,
                              String locationText,
                              LocalDateTime completedAt,
                              Boolean isOverdue,
                              Integer rating) {
    }
}
