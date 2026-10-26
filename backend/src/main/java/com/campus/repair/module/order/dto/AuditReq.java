package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotNull;

public record AuditReq(@NotNull(message = "pass 不能为空") Boolean pass,
                       String reason) {
}
