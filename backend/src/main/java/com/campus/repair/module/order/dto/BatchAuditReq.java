package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量审核（R2-F3-2）：逐条处理，返回每条结果，不整批回滚。
 */
public record BatchAuditReq(@NotEmpty(message = "请选择要审核的工单")
                            @Size(max = 50, message = "单次最多 50 条") List<Long> ids,
                            @NotNull(message = "请选择审核结果") Boolean pass,
                            @Size(max = 200, message = "驳回理由需 200 字以内") String reason) {
}
