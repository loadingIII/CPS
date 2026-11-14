package com.campus.repair.module.stats;

import com.campus.repair.common.Result;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Role;
import com.campus.repair.module.stats.dto.OntimePointVO;
import com.campus.repair.module.stats.dto.WorkerPerfVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "12 统计与绩效")
@RestController
@RequestMapping("/api/stats")
@RequireRole({Role.ADMIN, Role.SYS_ADMIN})
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "工单量与按时完成率趋势")
    @GetMapping("/ontime")
    public Result<List<OntimePointVO>> ontime(@RequestParam(defaultValue = "14") int days) {
        return Result.ok(statsService.ontime(days));
    }

    @Operation(summary = "维修工绩效排行")
    @GetMapping("/workers")
    public Result<List<WorkerPerfVO>> workers(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(statsService.workerPerformance(days));
    }
}
