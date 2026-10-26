package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotNull;

public record AssignReq(@NotNull(message = "workerId 不能为空") Long workerId) {
}
