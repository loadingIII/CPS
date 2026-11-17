package com.campus.repair.module.agent.dto;

/**
 * 报修建议结果。
 *
 * @param faultType  7 类之一；无法判断时为 null
 * @param urgency    NORMAL / URGENT
 * @param confidence 0–1，低于 0.6 时前端不自动填表
 * @param reason     不超过 20 字的理由
 * @param degraded   是否走了关键词兜底
 */
public record ReportSuggestionResp(String faultType,
                                   String urgency,
                                   Double confidence,
                                   String reason,
                                   boolean degraded) {
}
