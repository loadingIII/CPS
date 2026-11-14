package com.campus.repair.module.agent;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.agent.dto.AgentChatReq;
import com.campus.repair.module.agent.dto.AgentChatResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "10 智能助手")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "知识库检索问答（RAG 雏形，报修人可用）")
    @RequireRole(Role.REPORTER)
    @PostMapping("/chat")
    public Result<AgentChatResp> chat(@Valid @RequestBody AgentChatReq req) {
        return Result.ok(agentService.chat(req.message()));
    }
}
