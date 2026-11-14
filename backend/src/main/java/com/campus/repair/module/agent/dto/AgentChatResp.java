package com.campus.repair.module.agent.dto;

import java.util.List;

/**
 * 智能助手回答：answer 为可直接展示的文本，articles 为命中的知识库文章，
 * suggestedFaultType 用于前端一键带入报修表单。
 */
public record AgentChatResp(String answer, List<ArticleRef> articles, String suggestedFaultType) {

    public record ArticleRef(Long id, String title, String faultType, String excerpt) {
    }
}
