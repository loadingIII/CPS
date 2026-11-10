package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 手动派单请求。改派（覆盖已有承接人）时 reason 必填。
 */
public record AssignReq(@NotNull(message = "workerId 不能为空") Long workerId,
                        @Size(max = 200, message = "派单理由最长 200 字") String reason) {
}
