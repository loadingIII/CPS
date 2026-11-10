package com.campus.repair.module.order.statemachine;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.common.Role;
import com.campus.repair.module.order.RepairOrder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 工单状态机（集中式状态转移表驱动，规则 = 计划书 §2.2 的 13 条转移）。
 *
 * <p>硬性约定：
 * <ul>
 *   <li>任何状态变更必须经 fire() 校验「当前状态 + 动作 + 角色 + 操作人身份」；</li>
 *   <li>业务代码禁止散落 if(status == ...) 判断状态转移合法性；</li>
 *   <li>fire() 只修改内存实体，持久化由 OrderService 以乐观锁条件更新完成并同事务写 order_log。</li>
 * </ul>
 */
@Component
public class OrderStateMachine {

    /** 操作人身份要求 */
    public enum Identity { NONE, REPORTER, WORKER }

    @FunctionalInterface
    public interface Applier {
        void apply(RepairOrder order, Long extraWorkerId);
    }

    public record Rule(OrderStatus from, OrderAction action, Set<Role> roles,
                       Identity identity, boolean requireRemark,
                       Predicate<RepairOrder> guard, String guardFailMessage,
                       Applier apply) {
    }

    private static final List<Rule> RULES = List.of(
            // #1 审核通过：待审核 → 待处理
            new Rule(OrderStatus.PENDING_AUDIT, OrderAction.AUDIT_PASS,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, false,
                    o -> true, null,
                    (o, x) -> { o.setStatus(OrderStatus.PENDING); o.setAuditedAt(now()); }),
            // #2 驳回：待审核 → 已驳回（理由必填）
            new Rule(OrderStatus.PENDING_AUDIT, OrderAction.AUDIT_REJECT,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, true,
                    o -> true, null,
                    (o, x) -> { o.setStatus(OrderStatus.REJECTED); o.setAuditedAt(now()); }),
            // #3 补充重报：已驳回 → 待审核
            new Rule(OrderStatus.REJECTED, OrderAction.RESUBMIT,
                    Set.of(Role.REPORTER), Identity.REPORTER, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.PENDING_AUDIT)),
            // #4 报修人取消（待审核）
            new Rule(OrderStatus.PENDING_AUDIT, OrderAction.CANCEL,
                    Set.of(Role.REPORTER), Identity.REPORTER, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #5 报修人取消（待处理且未派单；已派单需管理员作废）
            new Rule(OrderStatus.PENDING, OrderAction.CANCEL,
                    Set.of(Role.REPORTER), Identity.REPORTER, false,
                    o -> o.getWorkerId() == null, "工单已派单，不能自行取消，需联系管理员作废",
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #6a 管理员作废（待审核）
            new Rule(OrderStatus.PENDING_AUDIT, OrderAction.CANCEL,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #6b 管理员作废（待处理）
            new Rule(OrderStatus.PENDING, OrderAction.CANCEL,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #6c 管理员作废（处理中）
            new Rule(OrderStatus.PROCESSING, OrderAction.CANCEL,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #7 报修人放弃（已驳回 → 已取消）
            new Rule(OrderStatus.REJECTED, OrderAction.ABANDON,
                    Set.of(Role.REPORTER), Identity.REPORTER, false,
                    o -> true, null,
                    (o, x) -> o.setStatus(OrderStatus.CANCELLED)),
            // #8 派单：待处理 → 待处理（回填维修工；资格校验由服务层完成，支持改派覆盖）
            new Rule(OrderStatus.PENDING, OrderAction.ASSIGN,
                    Set.of(Role.ADMIN, Role.SYS_ADMIN), Identity.NONE, false,
                    o -> true, null,
                    (o, x) -> { o.setWorkerId(x); o.setAssignedAt(now()); }),
            // #8b 抢单：维修工自助承接（仅未派单工单；资格与容量由服务层校验，先到先得）
            // 抢到即接单：直接进入处理中，不再要求二次确认
            new Rule(OrderStatus.PENDING, OrderAction.CLAIM,
                    Set.of(Role.REPAIRER), Identity.NONE, false,
                    o -> o.getWorkerId() == null, "手慢了，该工单已被其他师傅抢走",
                    (o, x) -> {
                        o.setWorkerId(x);
                        o.setAssignedAt(now());
                        o.setAcceptedAt(now());
                        o.setAssignExpiresAt(null);
                        o.setStatus(OrderStatus.PROCESSING);
                    }),
            // #9 接单：待处理 → 处理中（仅被派人；并发由乐观锁保证，§2.4）
            new Rule(OrderStatus.PENDING, OrderAction.ACCEPT,
                    Set.of(Role.REPAIRER), Identity.WORKER, false,
                    o -> true, null,
                    (o, x) -> {
                        o.setStatus(OrderStatus.PROCESSING);
                        o.setAcceptedAt(now());
                        o.setAssignExpiresAt(null);
                    }),
            // #10 拒单：待处理 → 待处理（清空承接信息回池，理由必填）
            new Rule(OrderStatus.PENDING, OrderAction.REJECT_ASSIGN,
                    Set.of(Role.REPAIRER), Identity.WORKER, true,
                    o -> true, null,
                    (o, x) -> {
                        o.setWorkerId(null);
                        o.setAssignedAt(null);
                        o.setAcceptedAt(null);
                        o.setAssignExpiresAt(null);
                        o.setDueAt(null);
                    }),
            // #11 完工：处理中 → 已完成
            new Rule(OrderStatus.PROCESSING, OrderAction.FINISH,
                    Set.of(Role.REPAIRER), Identity.WORKER, false,
                    o -> true, null,
                    (o, x) -> { o.setStatus(OrderStatus.COMPLETED); o.setCompletedAt(now()); }),
            // #12 转单：处理中 → 待处理（清空承接信息，理由必填；加分项）
            new Rule(OrderStatus.PROCESSING, OrderAction.TRANSFER,
                    Set.of(Role.REPAIRER), Identity.WORKER, true,
                    o -> true, null,
                    (o, x) -> {
                        o.setWorkerId(null);
                        o.setAssignedAt(null);
                        o.setAcceptedAt(null);
                        o.setAssignExpiresAt(null);
                        o.setDueAt(null);
                        o.setStatus(OrderStatus.PENDING);
                    }),
            // #13 评价：已完成 → 已评价（一单一评价由服务层 + uk_order 双重保证）
            new Rule(OrderStatus.COMPLETED, OrderAction.RATE,
                    Set.of(Role.REPORTER), Identity.REPORTER, false,
                    o -> true, null,
                    (o, x) -> { o.setStatus(OrderStatus.EVALUATED); o.setRatedAt(now()); })
    );

    /**
     * 校验并执行状态转移（修改内存实体，不落库）。
     *
     * @param extraWorkerId ASSIGN 动作的目标维修工
     * @throws BusinessException 非法转移 40901 / 无权限 40301 / 理由缺失 40001 / 前置不满足 40901
     */
    public void fire(RepairOrder order, OrderAction action, Role role, Long operatorId,
                     String remark, Long extraWorkerId) {
        List<Rule> candidates = RULES.stream()
                .filter(r -> r.from() == order.getStatus() && r.action() == action)
                .toList();
        if (candidates.isEmpty()) {
            throw conflict("工单当前状态[" + order.getStatus().label() + "]不允许执行[" + action.label() + "]");
        }
        Rule rule = candidates.stream()
                .filter(r -> r.roles().contains(role))
                .findFirst()
                .orElseThrow(() -> forbidden("角色[" + role + "]无权对[" + order.getStatus().label()
                        + "]工单执行[" + action.label() + "]"));

        if (rule.requireRemark() && (remark == null || remark.isBlank())) {
            throw BusinessException.param(action.label() + "必须填写理由");
        }
        switch (rule.identity()) {
            case REPORTER -> {
                if (!operatorId.equals(order.getReporterId())) {
                    throw forbidden("只能操作自己提交的工单");
                }
            }
            case WORKER -> {
                if (order.getWorkerId() == null || !operatorId.equals(order.getWorkerId())) {
                    throw forbidden("只能操作派给自己的工单");
                }
            }
            case NONE -> { }
        }
        if (rule.guard() != null && !rule.guard().test(order)) {
            throw conflict(rule.guardFailMessage());
        }
        rule.apply().apply(order, extraWorkerId);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    private static BusinessException conflict(String msg) {
        return new BusinessException(ErrorCode.CONFLICT, msg);
    }

    private static BusinessException forbidden(String msg) {
        return new BusinessException(ErrorCode.FORBIDDEN, msg);
    }
}
