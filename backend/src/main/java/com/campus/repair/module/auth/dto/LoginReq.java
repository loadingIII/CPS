package com.campus.repair.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginReq(@NotBlank(message = "学工号不能为空") String jobNo,
                       @NotBlank(message = "密码不能为空") String password) {
}
