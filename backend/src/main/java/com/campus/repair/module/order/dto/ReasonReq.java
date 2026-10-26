package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotBlank;

/** 拒单 / 转单等需要理由的动作 */
public record ReasonReq(@NotBlank(message = "理由不能为空") String reason) {
}
