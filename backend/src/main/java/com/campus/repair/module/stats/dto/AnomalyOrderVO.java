package com.campus.repair.module.stats.dto;

import java.time.LocalDateTime;

/**
 * 异常工单看板行（R3）：只读，不新增状态。
 */
public record AnomalyOrderVO(String type,
                             String typeLabel,
                             Long orderId,
                             String orderNo,
                             String campusName,
                             String locationText,
                             String status,
                             String workerName,
                             long ageHours,
                             Integer dispatchCount,
                             Integer rejectCount,
                             LocalDateTime submittedAt,
                             LocalDateTime dueAt,
                             String relatedOrderNo) {
}
