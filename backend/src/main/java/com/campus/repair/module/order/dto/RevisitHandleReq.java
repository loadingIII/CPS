package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 回访任务处理：HANDLED 已回访 / CLOSED 关闭。
 */
public record RevisitHandleReq(@NotBlank(message = "请选择处理结果") String status,
                               @Size(max = 300, message = "回访说明需 300 字以内") String remark) {
}
