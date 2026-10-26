package com.campus.repair.module.auth.dto;

import com.campus.repair.common.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserCreateReq(@NotBlank(message = "学工号不能为空") String jobNo,
                            @NotBlank(message = "姓名不能为空") String name,
                            @NotNull(message = "角色不能为空") Role role,
                            String phone,
                            String department,
                            String address,
                            @NotBlank(message = "密码不能为空")
                            @Size(min = 6, max = 32, message = "密码长度 6-32 位") String password) {
}
