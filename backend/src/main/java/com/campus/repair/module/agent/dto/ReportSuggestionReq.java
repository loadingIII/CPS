package com.campus.repair.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 报修建议请求：只收描述文本，不落库。 */
public record ReportSuggestionReq(
        @NotBlank(message = "请描述故障现象")
        @Size(max = 1000, message = "描述需 1000 字以内") String description) {
}
