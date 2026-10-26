package com.campus.repair.module.worker;

import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.worker.dto.EligibleWorkerVO;
import com.campus.repair.module.worker.dto.WorkerCreateReq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "05 维修工管理")
@RestController
@RequestMapping("/api/workers")
@RequireRole({Role.ADMIN, Role.SYS_ADMIN})
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Operation(summary = "维修工分页列表（含技能/范围/负载）")
    @GetMapping
    public Result<PageVO<WorkerVO>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long skillId,
                                         @RequestParam(required = false) Boolean onDuty,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(workerService.page(keyword, skillId, onDuty, page, pageSize));
    }

    @Operation(summary = "维修工详情")
    @GetMapping("/{id}")
    public Result<WorkerVO> detail(@PathVariable Long id) {
        return Result.ok(workerService.detail(id));
    }

    @Operation(summary = "我的绩效档案（维修工本人：评分/负载/技能）")
    @RequireRole(Role.REPAIRER)
    @GetMapping("/me")
    public Result<WorkerVO> me() {
        return Result.ok(workerService.detail(com.campus.repair.common.AuthContext.userId()));
    }

    @Operation(summary = "新建维修工（账号+档案+技能+范围）")
    @PostMapping
    public Result<WorkerVO> create(@Valid @RequestBody WorkerCreateReq req) {
        return Result.ok(workerService.create(req));
    }

    @Operation(summary = "修改维修工档案")
    @PutMapping("/{id}")
    public Result<WorkerVO> update(@PathVariable Long id,
                                   @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String phone = (String) body.get("phone");
        List<Long> skillIds = toLongList(body.get("skillIds"));
        List<Long> scopeIds = toLongList(body.get("scopeIds"));
        Integer loadLimit = body.get("loadLimit") == null ? null : ((Number) body.get("loadLimit")).intValue();
        Boolean onDuty = body.get("onDuty") == null ? null : (Boolean) body.get("onDuty");
        return Result.ok(workerService.update(id, name, phone, skillIds, scopeIds, loadLimit, onDuty));
    }

    @Operation(summary = "在岗状态切换")
    @PutMapping("/{id}/on-duty")
    public Result<Void> setOnDuty(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean onDuty = body.get("onDuty");
        if (onDuty == null) {
            return Result.fail(com.campus.repair.common.ErrorCode.PARAM_ERROR, "onDuty 不能为空");
        }
        workerService.setOnDuty(id, onDuty);
        return Result.ok();
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object o) {
        if (o == null) {
            return null;
        }
        return ((List<Object>) o).stream().map(x -> ((Number) x).longValue()).toList();
    }
}
