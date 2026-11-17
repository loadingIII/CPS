package com.campus.repair.module.kb;

import jakarta.validation.constraints.NotBlank;

/** 教程页自助事件：SOLVED / UNSOLVED。 */
public record KbEventReq(@NotBlank(message = "请选择事件类型") String eventType) {
}
