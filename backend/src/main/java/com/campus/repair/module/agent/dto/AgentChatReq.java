package com.campus.repair.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param message        用户问题
 * @param conversationId 前端会话标识（用于多轮记忆，缺省时同一用户共用 default）
 */
public record AgentChatReq(
        @NotBlank(message = "请描述一下故障现象")
        @Size(max = 200, message = "一次最多输入 200 字") String message,

        @Size(max = 64, message = "会话标识过长")
        String conversationId) {

    public AgentChatReq(String message) {
        this(message, null);
    }
}
