package com.campus.repair.module.auth.dto;

import com.campus.repair.common.Role;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录：role 为可选登录入口（小程序区分报修人/维修工），
 * 为空时不校验角色，兼容管理端与既有脚本。
 */
public record LoginReq(@NotBlank(message = "学工号不能为空") String jobNo,
                       @NotBlank(message = "密码不能为空") String password,
                       Role role) {
}
