package com.campus.repair.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatReq(
        @NotBlank(message = "请描述一下故障现象")
        @Size(max = 200, message = "一次最多输入 200 字") String message) {
}
