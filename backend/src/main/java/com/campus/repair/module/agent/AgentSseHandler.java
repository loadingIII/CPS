package com.campus.repair.module.agent;

import com.campus.repair.common.BusinessException;
import com.campus.repair.module.agent.dto.AgentChatResp;
import com.campus.repair.module.agent.rag.RagResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 输出：把 Agent 的增量文本转成事件流。
 *
 * <pre>
 * event: start  data: {"conversationId":"c_xxx","mode":"ai"}
 * event: refs   data: [{id,title,faultType,excerpt}]
 * event: delta  data: {"t":"..."}
 * event: done   data: {"suggestedFaultType":"空调家电","elapsedMs":1234}
 * event: error  data: {"code":"AI_ERROR","message":"..."}
 * </pre>
 */
@Component
public class AgentSseHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentSseHandler.class);

    private final AgentService agentService;
    private final AgentProperties props;

    public AgentSseHandler(AgentService agentService, AgentProperties props) {
        this.agentService = agentService;
        this.props = props;
    }

    public SseEmitter stream(String message, String conversationId) {
        long timeoutMs = (props.getTimeoutSeconds() + 15L) * 1000L;
        SseEmitter emitter = new SseEmitter(timeoutMs);
        String conv = StringUtils.hasText(conversationId)
                ? conversationId.trim()
                : UUID.randomUUID().toString().substring(0, 8);

        AgentService.AgentStream stream;
        try {
            stream = agentService.stream(message, conv);
        } catch (BusinessException e) {
            sendError(emitter, "BAD_REQUEST", e.getMessage());
            emitter.complete();
            return emitter;
        } catch (Exception e) {
            log.error("[Agent] 建立流式会话失败", e);
            sendError(emitter, "AI_ERROR", "智能助手暂时不可用，请稍后再试");
            emitter.complete();
            return emitter;
        }

        send(emitter, "start", Map.of(
                "conversationId", conv,
                "mode", stream.degraded() ? "fallback" : "ai"));

        if (stream.degraded()) {
            AgentChatResp preset = stream.preset();
            send(emitter, "refs", preset.articles());
            send(emitter, "delta", Map.of("t", preset.answer()));
            send(emitter, "done", donePayload(preset.suggestedFaultType(), 0));
            emitter.complete();
            return emitter;
        }

        long startedAt = System.currentTimeMillis();
        AtomicBoolean finished = new AtomicBoolean(false);

        Disposable disposable = stream.content().subscribe(
                text -> {
                    if (text == null || text.isEmpty() || finished.get()) {
                        return;
                    }
                    send(emitter, "delta", Map.of("t", text));
                },
                error -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    log.warn("[Agent] 流式回答中断：{}", error.toString());
                    sendError(emitter, error instanceof java.util.concurrent.TimeoutException
                            ? "AI_TIMEOUT" : "AI_ERROR",
                            error instanceof java.util.concurrent.TimeoutException
                                    ? "回答超时了，请重试或直接提交报修"
                                    : "智能助手开小差了，请重试或直接提交报修");
                    safeComplete(emitter);
                },
                () -> {
                    if (!finished.compareAndSet(false, true)) {
                        return;
                    }
                    RagResult result = stream.tool() == null ? RagResult.empty() : stream.tool().lastResult();
                    List<AgentChatResp.ArticleRef> refs = agentService.toRefs(result);
                    if (!refs.isEmpty()) {
                        send(emitter, "refs", refs);
                    }
                    send(emitter, "done", donePayload(
                            agentService.suggestedType(result, message),
                            System.currentTimeMillis() - startedAt));
                    safeComplete(emitter);
                });

        emitter.onCompletion(disposable::dispose);
        emitter.onError(e -> disposable.dispose());
        emitter.onTimeout(() -> {
            disposable.dispose();
            safeComplete(emitter);
        });
        return emitter;
    }

    private Map<String, Object> donePayload(String faultType, long elapsedMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suggestedFaultType", faultType == null ? "" : faultType);
        payload.put("elapsedMs", elapsedMs);
        return payload;
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("[Agent] SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message == null ? "智能助手暂时不可用" : message);
        send(emitter, "error", payload);
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // 已经完成或客户端断开
        }
    }
}
