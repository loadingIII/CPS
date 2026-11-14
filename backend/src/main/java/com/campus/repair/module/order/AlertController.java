package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.order.dto.AlertVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 超时预警工作台（R2-F8-2/F8-3）：预警事件由 DispatchScheduler 写入，管理员在此处置。
 */
@Tag(name = "08 超时预警")
@RestController
@RequestMapping("/api/alerts")
@RequireRole({Role.ADMIN, Role.SYS_ADMIN})
public class AlertController {

    private final OverdueAlertMapper alertMapper;

    public AlertController(OverdueAlertMapper alertMapper) {
        this.alertMapper = alertMapper;
    }

    @Operation(summary = "预警列表（默认只看未处理）")
    @GetMapping
    public Result<PageVO<AlertVO>> page(@RequestParam(defaultValue = "false") boolean handled,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long pageSize) {
        long size = Math.max(1, Math.min(pageSize, 50));
        long offset = (Math.max(page, 1) - 1) * size;
        List<AlertVO> list = alertMapper.selectPageByHandled(handled, offset, size);
        return Result.ok(new PageVO<>(list, alertMapper.countByHandled(handled)));
    }

    @Operation(summary = "标记预警已处理")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        alertMapper.update(null, new LambdaUpdateWrapper<OverdueAlert>()
                .eq(OverdueAlert::getId, id)
                .eq(OverdueAlert::getHandled, false)
                .set(OverdueAlert::getHandled, true));
        return Result.ok();
    }
}
