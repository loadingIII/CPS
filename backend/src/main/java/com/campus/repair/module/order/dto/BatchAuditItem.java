package com.campus.repair.module.order.dto;

/**
 * 批量审核单条结果：失败不影响其他工单。
 */
public record BatchAuditItem(Long id, boolean success, String message) {
}
