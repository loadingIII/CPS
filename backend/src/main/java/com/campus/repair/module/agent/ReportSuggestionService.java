package com.campus.repair.module.agent;

import com.campus.repair.common.BusinessException;
import com.campus.repair.module.agent.dto.ReportSuggestionResp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 报修建议：把自由文本描述转成故障类型 + 紧急度。
 *
 * <p>只做“建议”，不写工单、不改状态；用户点采纳才填表。
 * 安全关键词由规则层兜底，模型不可用时回落关键词分类。</p>
 */
@Service
public class ReportSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ReportSuggestionService.class);

    /** 7 类故障字典（含「其他」，与 skill 表一致） */
    private static final List<String> FAULT_TYPES = List.of(
            "水电", "木工门窗", "网络多媒体", "空调家电", "电梯", "实验设备", "其他");

    /** 命中即强制建议紧急；不依赖模型 */
    private static final List<String> URGENT_TERMS = List.of(
            "漏电", "触电", "带电", "爆管", "大量漏水", "电梯困人", "困人", "冒烟", "起火",
            "着火", "燃气", "煤气", "玻璃破裂", "玻璃碎", "坠落", "倒塌");

    private static final String SYSTEM = """
            你是校园报修系统的分诊助手。只输出一个 JSON 对象，不要 Markdown、不要解释。
            JSON 字段：
            - faultType：只能是「水电」「木工门窗」「网络多媒体」「空调家电」「电梯」「实验设备」「其他」之一
            - urgency：只能是 NORMAL 或 URGENT
            - confidence：0 到 1 之间的小数
            - reason：不超过 20 个汉字
            判断规则：涉及漏电、触电、爆管、大量漏水、电梯困人、冒烟、起火、燃气、玻璃破裂等安全风险时，urgency 必须为 URGENT。
            如果描述信息不足，faultType 返回 null，confidence 低于 0.4。
            """;

    private final KeywordAgentFallback fallback;
    private final AgentProperties props;
    private final ObjectProvider<ChatClient> suggestionClientProvider;
    private final ObjectMapper objectMapper;

    public ReportSuggestionService(KeywordAgentFallback fallback,
                                   AgentProperties props,
                                   @Qualifier("suggestionChatClient")
                                   ObjectProvider<ChatClient> suggestionClientProvider,
                                   ObjectMapper objectMapper) {
        this.fallback = fallback;
        this.props = props;
        this.suggestionClientProvider = suggestionClientProvider;
        this.objectMapper = objectMapper;
    }

    public ReportSuggestionResp suggest(String description) {
        String text = description == null ? "" : description.strip();
        if (text.length() < 2) {
            throw BusinessException.param("请先描述故障现象");
        }
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }

        String ruleType = fallback.classify(text);
        boolean ruleUrgent = containsUrgentTerm(text);

        ReportSuggestionResp llm = askModel(text);
        if (llm == null) {
            return new ReportSuggestionResp(
                    ruleType,
                    ruleUrgent ? "URGENT" : "NORMAL",
                    ruleType == null ? 0.2 : 0.62,
                    ruleUrgent ? "描述包含安全风险关键词" : "按故障关键词匹配",
                    true);
        }

        String type = normalizeType(llm.faultType());
        String urgency = ruleUrgent ? "URGENT" : normalizeUrgency(llm.urgency());
        double confidence = clamp(llm.confidence() == null ? 0.5 : llm.confidence());
        String reason = StringUtils.hasText(llm.reason()) ? llm.reason().strip() : "按描述判断";
        if (reason.length() > 20) {
            reason = reason.substring(0, 20);
        }
        if (ruleUrgent) {
            reason = "描述包含安全风险关键词";
            confidence = Math.max(confidence, 0.9);
        }
        return new ReportSuggestionResp(type, urgency, confidence, reason, false);
    }

    private ReportSuggestionResp askModel(String text) {
        if (!props.chatUsable()) {
            return null;
        }
        ChatClient client = suggestionClientProvider.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            String raw = client.prompt()
                    .system(SYSTEM)
                    .user("报修描述：" + text)
                    .call()
                    .content();
            JsonNode node = parseJson(raw);
            if (node == null) {
                return null;
            }
            String type = node.path("faultType").isNull() ? null : node.path("faultType").asText(null);
            String urgency = node.path("urgency").asText("NORMAL");
            Double confidence = node.hasNonNull("confidence") ? node.get("confidence").asDouble() : 0.5;
            String reason = node.path("reason").asText("");
            return new ReportSuggestionResp(type, urgency, confidence, reason, false);
        } catch (Exception e) {
            log.warn("[Suggest] 模型建议失败，回落关键词：{}", e.getMessage());
            return null;
        }
    }

    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String t = type.strip();
        return FAULT_TYPES.contains(t) ? t : null;
    }

    private String normalizeUrgency(String urgency) {
        return "URGENT".equalsIgnoreCase(urgency) ? "URGENT" : "NORMAL";
    }

    private boolean containsUrgentTerm(String text) {
        for (String term : URGENT_TERMS) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
