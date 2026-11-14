package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.order.dto.RevisitHandleReq;
import com.campus.repair.module.order.dto.RevisitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 差评回访任务处理（R2-F7-3）。任务由评价 ≤2 星时自动生成。
 */
@Tag(name = "10 差评回访")
@RestController
@RequestMapping("/api/revisits")
@RequireRole({Role.ADMIN, Role.SYS_ADMIN})
public class RevisitController {

    private static final Set<String> ALLOWED = Set.of("HANDLED", "CLOSED");

    private final RevisitTaskMapper revisitTaskMapper;

    public RevisitController(RevisitTaskMapper revisitTaskMapper) {
        this.revisitTaskMapper = revisitTaskMapper;
    }

    @Operation(summary = "回访任务列表")
    @GetMapping
    public Result<PageVO<RevisitVO>> page(@RequestParam(defaultValue = "PENDING") String status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long pageSize) {
        long size = Math.max(1, Math.min(pageSize, 50));
        long offset = (Math.max(page, 1) - 1) * size;
        List<RevisitVO> list = revisitTaskMapper.selectPageByStatus(status, offset, size);
        return Result.ok(new PageVO<>(list, revisitTaskMapper.countByStatus(status)));
    }

    @Operation(summary = "处理回访任务")
    @PutMapping("/{id}")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody RevisitHandleReq req) {
        if (!ALLOWED.contains(req.status())) {
            throw BusinessException.param("处理结果只能是 HANDLED 或 CLOSED");
        }
        RevisitTask task = revisitTaskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.notFound("回访任务不存在");
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw BusinessException.conflict("该回访任务已处理");
        }
        int rows = revisitTaskMapper.update(null, new LambdaUpdateWrapper<RevisitTask>()
                .eq(RevisitTask::getId, id)
                .eq(RevisitTask::getStatus, "PENDING")
                .set(RevisitTask::getStatus, req.status())
                .set(RevisitTask::getHandledBy, AuthContext.userId())
                .set(RevisitTask::getHandledAt, LocalDateTime.now())
                .set(RevisitTask::getRemark, req.remark()));
        if (rows != 1) {
            throw BusinessException.conflict("该回访任务已被其他管理员处理");
        }
        return Result.ok();
    }

    @Operation(summary = "待回访任务数")
    @GetMapping("/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(revisitTaskMapper.selectCount(new LambdaQueryWrapper<RevisitTask>()
                .eq(RevisitTask::getStatus, "PENDING")));
    }
}
