package com.campus.repair.module.agent;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.agent.dto.ReportSuggestionReq;
import com.campus.repair.module.agent.dto.ReportSuggestionResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "13 报修 AI 建议")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ReportSuggestionService suggestionService;

    public AiController(ReportSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Operation(summary = "根据描述建议故障类型与紧急度（不写工单）")
    @RequireRole({Role.REPORTER})
    @PostMapping("/report-suggestion")
    public Result<ReportSuggestionResp> reportSuggestion(
            @Valid @RequestBody ReportSuggestionReq req) {
        return Result.ok(suggestionService.suggest(req.description()));
    }
}
