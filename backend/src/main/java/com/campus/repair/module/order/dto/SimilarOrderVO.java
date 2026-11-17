package com.campus.repair.module.order.dto;

import java.time.LocalDateTime;

/**
 * 审核页「同位置历史报修」：同校区 + 同故障类型 + 位置相似，只读。
 */
public record SimilarOrderVO(Long id,
                             String orderNo,
                             String locationText,
                             String faultType,
                             String status,
                             String workerName,
                             LocalDateTime submittedAt,
                             LocalDateTime completedAt,
                             Integer rating) {
}
