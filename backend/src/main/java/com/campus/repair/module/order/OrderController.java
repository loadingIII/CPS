package com.campus.repair.module.order;

import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.order.dto.AuditReq;
import com.campus.repair.module.order.dto.AssignReq;
import com.campus.repair.module.order.dto.DispatchInfoVO;
import com.campus.repair.module.order.dto.EvaluationReq;
import com.campus.repair.module.order.dto.FinishReq;
import com.campus.repair.module.order.dto.MapOrderVO;
import com.campus.repair.module.order.dto.OrderCreatedVO;
import com.campus.repair.module.order.dto.ReasonReq;
import com.campus.repair.module.order.dto.ResubmitReq;
import com.campus.repair.module.order.dto.SubmitReq;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.worker.dto.EligibleWorkerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "07 工单业务")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ---------------- 报修人 ----------------

    @Operation(summary = "提交报修（F2）")
    @RequireRole(Role.REPORTER)
    @PostMapping
    public Result<OrderCreatedVO> submit(@Valid @RequestBody SubmitReq req) {
        return Result.ok(orderService.submit(req));
    }

    @Operation(summary = "我的报修列表（F6-2）")
    @RequireRole(Role.REPORTER)
    @GetMapping("/my")
    public Result<PageVO<OrderListVO>> my(@RequestParam(required = false) OrderStatus status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.pageMy(status, page, pageSize));
    }

    @Operation(summary = "补充重报（已驳回单）")
    @RequireRole(Role.REPORTER)
    @PostMapping("/{id}/resubmit")
    public Result<Void> resubmit(@PathVariable Long id, @Valid @RequestBody ResubmitReq req) {
        orderService.resubmit(id, req);
        return Result.ok();
    }

    @Operation(summary = "报修人撤单（待审核/未派单的待处理）")
    @RequireRole({Role.REPORTER, Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "报修人放弃（已驳回单）")
    @RequireRole(Role.REPORTER)
    @PostMapping("/{id}/abandon")
    public Result<Void> abandon(@PathVariable Long id) {
        orderService.abandon(id);
        return Result.ok();
    }

    @Operation(summary = "提交评价（F7-1）")
    @RequireRole(Role.REPORTER)
    @PostMapping("/{id}/evaluation")
    public Result<Void> evaluate(@PathVariable Long id, @Valid @RequestBody EvaluationReq req) {
        orderService.evaluate(id, req);
        return Result.ok();
    }

    // ---------------- 维修工 ----------------

    @Operation(summary = "派给我的工单列表（F5-1）")
    @RequireRole(Role.REPAIRER)
    @GetMapping("/mine-assigned")
    public Result<PageVO<OrderListVO>> mineAssigned(@RequestParam(required = false) OrderStatus status,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.mineAssigned(status, page, pageSize));
    }

    @Operation(summary = "接单（乐观锁幂等）")
    @RequireRole(Role.REPAIRER)
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        orderService.accept(id);
        return Result.ok();
    }

    @Operation(summary = "抢单大厅：我可承接的待派单工单（F4-4 自助抢单，抢到即接单）")
    @RequireRole(Role.REPAIRER)
    @GetMapping("/grab-pool")
    public Result<PageVO<OrderListVO>> grabPool(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(orderService.grabPool(page, pageSize));
    }

    @Operation(summary = "抢单（先到先得，抢到即接单）")
    @RequireRole(Role.REPAIRER)
    @PostMapping("/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        orderService.claim(id);
        return Result.ok();
    }

    @Operation(summary = "维修工工单地图：已接单 + 可抢单的坐标点")
    @RequireRole(Role.REPAIRER)
    @GetMapping("/worker-map")
    public Result<List<MapOrderVO>> workerMap() {
        return Result.ok(orderService.workerMap());
    }

    @Operation(summary = "拒单（回待处理池）")
    @RequireRole(Role.REPAIRER)
    @PostMapping("/{id}/reject-assign")
    public Result<Void> rejectAssign(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        orderService.rejectAssign(id, req);
        return Result.ok();
    }

    @Operation(summary = "完工上报（F5-4）")
    @RequireRole(Role.REPAIRER)
    @PostMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, @Valid @RequestBody FinishReq req) {
        orderService.finish(id, req);
        return Result.ok();
    }

    @Operation(summary = "转单（回待处理池，加分项）")
    @RequireRole(Role.REPAIRER)
    @PostMapping("/{id}/transfer")
    public Result<Void> transfer(@PathVariable Long id, @Valid @RequestBody ReasonReq req) {
        orderService.transfer(id, req);
        return Result.ok();
    }

    // ---------------- 管理员 ----------------

    @Operation(summary = "管理工作台工单统计")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @GetMapping("/statistics")
    public Result<OrderStatisticsVO> statistics() {
        return Result.ok(orderService.statistics());
    }

    @Operation(summary = "工单列表（管理视角，审核队列复用；dispatchState=POOL/OFFERED 拆分待派单与已派待接）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @GetMapping
    public Result<PageVO<OrderListVO>> adminPage(@RequestParam(required = false) OrderStatus status,
                                                 @RequestParam(required = false) Urgency urgency,
                                                 @RequestParam(required = false) String faultType,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) DispatchState dispatchState,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.pageAdmin(status, urgency, faultType, keyword,
                dispatchState, page, pageSize));
    }

    @Operation(summary = "审核（通过/驳回，F3-2）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditReq req) {
        orderService.audit(id, req);
        return Result.ok();
    }

    @Operation(summary = "作废工单（管理员）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/{id}/void")
    public Result<Void> voidOrder(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "该工单的维修工派单看板（资格判定，F4-1）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @GetMapping("/{id}/eligible-workers")
    public Result<List<EligibleWorkerVO>> eligibleWorkers(@PathVariable Long id) {
        return Result.ok(orderService.eligibleWorkers(id));
    }

    @Operation(summary = "手动派单（F4-2，支持改派；改派必须填写理由）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignReq req) {
        orderService.assign(id, req);
        return Result.ok();
    }

    @Operation(summary = "派单调度信息（子状态/来源/次数/剩余接单时间）")
    @GetMapping("/{id}/dispatch-info")
    public Result<DispatchInfoVO> dispatchInfo(@PathVariable Long id) {
        return Result.ok(orderService.dispatchInfo(id));
    }

    @Operation(summary = "管理员开放抢单（应对难派单）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/{id}/broadcast")
    public Result<Void> broadcast(@PathVariable Long id) {
        orderService.broadcast(id);
        return Result.ok();
    }

    // ---------------- 通用（登录即可，服务内做数据权限） ----------------

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @Operation(summary = "状态时间线（F6-1）")
    @GetMapping("/{id}/logs")
    public Result<List<OrderLogVO>> logs(@PathVariable Long id) {
        return Result.ok(orderService.logs(id));
    }

    @Operation(summary = "查看评价")
    @GetMapping("/{id}/evaluation")
    public Result<Evaluation> evaluation(@PathVariable Long id) {
        return Result.ok(orderService.getEvaluation(id));
    }
}
