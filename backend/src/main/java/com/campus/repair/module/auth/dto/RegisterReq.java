package com.campus.repair.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 师生自助注册：只开放报修人角色；维修工与管理员账号仍由后勤/系统管理员开通。
 *
 * <p>课程演示环境未接短信与校园统一认证，学工号唯一性由数据库唯一键兜底。
 */
public record RegisterReq(
        @NotBlank(message = "请填写学工号")
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,20}$", message = "学工号需 4-20 位字母、数字、下划线或短横线")
        String jobNo,

        @NotBlank(message = "请填写姓名")
        @Size(min = 2, max = 20, message = "姓名需 2-20 个字")
        String name,

        @NotBlank(message = "请填写手机号")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的 11 位手机号")
        String phone,

        @NotBlank(message = "请设置密码")
        @Size(min = 6, max = 32, message = "密码长度需 6-32 位")
        String password
) {
}
