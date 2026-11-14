package com.campus.repair.module.agent;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.agent.dto.AgentChatReq;
import com.campus.repair.module.agent.dto.AgentChatResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Tag(name = "10 智能助手")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final AgentSseHandler sseHandler;
    private final AgentHealthService healthService;

    public AgentController(AgentService agentService,
                           AgentSseHandler sseHandler,
                           AgentHealthService healthService) {
        this.agentService = agentService;
        this.sseHandler = sseHandler;
        this.healthService = healthService;
    }

    @Operation(summary = "流式问答（SSE，报修人可用）")
    @RequireRole(Role.REPORTER)
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AgentChatReq req) {
        return sseHandler.stream(req.message(), req.conversationId());
    }

    @Operation(summary = "同步问答（低版本基础库 / 调试用）")
    @RequireRole(Role.REPORTER)
    @PostMapping("/chat")
    public Result<AgentChatResp> chat(@Valid @RequestBody AgentChatReq req) {
        return Result.ok(agentService.chat(req.message(), req.conversationId()));
    }

    @Operation(summary = "智能助手自检（管理员）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @GetMapping("/health")
    public Result<Map<String, Object>> health(
            @RequestParam(defaultValue = "false") boolean deep) {
        return Result.ok(healthService.check(deep));
    }
}
