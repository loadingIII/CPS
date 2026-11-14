package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 重复工单合并（R2-F3-3）：把当前单并入主单。
 */
public record MergeReq(@NotNull(message = "请选择主工单") Long mainOrderId,
                       @Size(max = 200, message = "合并说明需 200 字以内") String reason) {
}
