package com.campus.repair.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordReq(@NotBlank(message = "新密码不能为空") String newPassword) {
}
