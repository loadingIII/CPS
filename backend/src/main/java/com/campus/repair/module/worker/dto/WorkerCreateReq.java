package com.campus.repair.module.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkerCreateReq(@NotBlank(message = "工号不能为空") String jobNo,
                              @NotBlank(message = "姓名不能为空") String name,
                              String phone,
                              @NotBlank(message = "初始密码不能为空") String password,
                              @NotEmpty(message = "请至少选择一项技能") List<Long> skillIds,
                              @NotEmpty(message = "请至少选择一个负责校区") List<String> campusCodes,
                              Integer loadLimit) {
}
