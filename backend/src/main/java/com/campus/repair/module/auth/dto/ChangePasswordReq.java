package com.campus.repair.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordReq(@NotBlank(message = "旧密码不能为空") String oldPassword,
                                @NotBlank(message = "新密码不能为空")
                                @Size(min = 6, max = 32, message = "新密码长度 6-32 位") String newPassword) {
}
