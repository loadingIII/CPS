package com.campus.repair.module.order.statemachine;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.Role;
import com.campus.repair.module.order.RepairOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T06 状态机单元测试：覆盖计划书 §2.2 全部 13 条转移的正向用例 + 非法变体。
 * 纯逻辑测试，不依赖数据库。
 */
class OrderStateMachineTest {

    private static final Long REPORTER_ID = 100L;
    private static final Long WORKER_ID = 200L;
    private static final Long OTHER_ID = 300L;
    private static final Long NEW_WORKER_ID = 201L;

    private OrderStateMachine sm;

    @BeforeEach
    void setUp() {
        sm = new OrderStateMachine();
    }

    private RepairOrder order(OrderStatus status) {
        RepairOrder o = new RepairOrder();
        o.setId(1L);
        o.setStatus(status);
        o.setReporterId(REPORTER_ID);
        o.setWorkerId(null);
        o.setVersion(0);
        return o;
    }

    private void fire(RepairOrder o, OrderAction action, Role role, Long operator,
                      String remark, Long extraWorkerId) {
        sm.fire(o, action, role, operator, remark, extraWorkerId);
    }

    // ---------- #1/#2 审核 ----------

    @Test
    @DisplayName("#1 审核通过：待审核→待处理")
    void auditPass() {
        RepairOrder o = order(OrderStatus.PENDING_AUDIT);
        fire(o, OrderAction.AUDIT_PASS, Role.ADMIN, OTHER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getAuditedAt()).isNotNull();
    }

    @Test
    @DisplayName("#2 驳回必填理由；填写后 待审核→已驳回")
    void auditReject() {
        RepairOrder o = order(OrderStatus.PENDING_AUDIT);
        assertThatThrownBy(() -> fire(o, OrderAction.AUDIT_REJECT, Role.ADMIN, OTHER_ID, "", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
        fire(o, OrderAction.AUDIT_REJECT, Role.SYS_ADMIN, OTHER_ID, "描述不清", null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(o.getAuditedAt()).isNotNull();
    }

    // ---------- #3 重报 ----------

    @Test
    @DisplayName("#3 补充重报：本人可用，他人被拒")
    void resubmit() {
        RepairOrder o = order(OrderStatus.REJECTED);
        assertThatThrownBy(() -> fire(o, OrderAction.RESUBMIT, Role.REPORTER, OTHER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
        fire(o, OrderAction.RESUBMIT, Role.REPORTER, REPORTER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING_AUDIT);
    }

    // ---------- #4/#5/#6/#7 取消与放弃 ----------

    @Test
    @DisplayName("#4 报修人取消待审核单")
    void reporterCancelPendingAudit() {
        RepairOrder o = order(OrderStatus.PENDING_AUDIT);
        fire(o, OrderAction.CANCEL, Role.REPORTER, REPORTER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("#5 报修人取消待处理单：未派可撤，已派需管理员")
    void reporterCancelPending() {
        RepairOrder unassigned = order(OrderStatus.PENDING);
        fire(unassigned, OrderAction.CANCEL, Role.REPORTER, REPORTER_ID, null, null);
        assertThat(unassigned.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        RepairOrder assigned = order(OrderStatus.PENDING);
        assigned.setWorkerId(WORKER_ID);
        assertThatThrownBy(() -> fire(assigned, OrderAction.CANCEL, Role.REPORTER, REPORTER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40901);
    }

    @Test
    @DisplayName("#6 管理员作废处理中单；报修人作废处理中单为角色无权(40301)")
    void adminCancelProcessing() {
        RepairOrder o = order(OrderStatus.PROCESSING);
        o.setWorkerId(WORKER_ID);
        assertThatThrownBy(() -> fire(o, OrderAction.CANCEL, Role.REPORTER, REPORTER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
        RepairOrder o2 = order(OrderStatus.PROCESSING);
        fire(o2, OrderAction.CANCEL, Role.ADMIN, OTHER_ID, null, null);
        assertThat(o2.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("#7 放弃：已驳回→已取消（仅本人）")
    void abandon() {
        RepairOrder o = order(OrderStatus.REJECTED);
        fire(o, OrderAction.ABANDON, Role.REPORTER, REPORTER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ---------- #8 派单 ----------

    @Test
    @DisplayName("#8 派单回填维修工；维修工角色无权派单")
    void assign() {
        RepairOrder o = order(OrderStatus.PENDING);
        assertThatThrownBy(() -> fire(o, OrderAction.ASSIGN, Role.REPAIRER, WORKER_ID, null, NEW_WORKER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
        fire(o, OrderAction.ASSIGN, Role.ADMIN, OTHER_ID, null, NEW_WORKER_ID);
        assertThat(o.getWorkerId()).isEqualTo(NEW_WORKER_ID);
        assertThat(o.getAssignedAt()).isNotNull();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ---------- #8b 抢单 ----------

    @Test
    @DisplayName("#8b 抢单：未派单可抢，抢到即接单（直接进入处理中）；已被派单 40901；管理员无权抢单")
    void claim() {
        RepairOrder o = order(OrderStatus.PENDING);
        o.setAssignExpiresAt(java.time.LocalDateTime.now());
        fire(o, OrderAction.CLAIM, Role.REPAIRER, WORKER_ID, null, WORKER_ID);
        assertThat(o.getWorkerId()).isEqualTo(WORKER_ID);
        assertThat(o.getAssignedAt()).isNotNull();
        assertThat(o.getAcceptedAt()).isNotNull();
        assertThat(o.getAssignExpiresAt()).isNull();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);

        RepairOrder assigned = order(OrderStatus.PENDING);
        assigned.setWorkerId(OTHER_ID);
        assertThatThrownBy(() -> fire(assigned, OrderAction.CLAIM, Role.REPAIRER, WORKER_ID, null, WORKER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40901);

        assertThatThrownBy(() -> fire(order(OrderStatus.PENDING), OrderAction.CLAIM, Role.ADMIN, OTHER_ID, null, OTHER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
    }

    // ---------- #9/#10 接单与拒单 ----------

    @Test
    @DisplayName("#9 接单：仅被派人；他人被拒")
    void accept() {
        RepairOrder o = order(OrderStatus.PENDING);
        o.setWorkerId(WORKER_ID);
        assertThatThrownBy(() -> fire(o, OrderAction.ACCEPT, Role.REPAIRER, OTHER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
        fire(o, OrderAction.ACCEPT, Role.REPAIRER, WORKER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(o.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("#10 拒单必填理由；成功后清空承接信息回池")
    void rejectAssign() {
        RepairOrder o = order(OrderStatus.PENDING);
        o.setWorkerId(WORKER_ID);
        o.setAssignedAt(java.time.LocalDateTime.now());
        o.setAcceptedAt(java.time.LocalDateTime.now());
        o.setAssignExpiresAt(java.time.LocalDateTime.now());
        o.setDueAt(java.time.LocalDateTime.now());
        assertThatThrownBy(() -> fire(o, OrderAction.REJECT_ASSIGN, Role.REPAIRER, WORKER_ID, "  ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40001);
        fire(o, OrderAction.REJECT_ASSIGN, Role.REPAIRER, WORKER_ID, "技能不符", null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getWorkerId()).isNull();
        assertThat(o.getAssignedAt()).isNull();
        assertThat(o.getAcceptedAt()).isNull();
        assertThat(o.getAssignExpiresAt()).isNull();
        assertThat(o.getDueAt()).isNull();
    }

    // ---------- #11/#12 完工与转单 ----------

    @Test
    @DisplayName("#11 完工：处理中→已完成")
    void finish() {
        RepairOrder o = order(OrderStatus.PROCESSING);
        o.setWorkerId(WORKER_ID);
        fire(o, OrderAction.FINISH, Role.REPAIRER, WORKER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(o.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("#12 转单必填理由；成功后清空承接信息回待处理池")
    void transfer() {
        RepairOrder o = order(OrderStatus.PROCESSING);
        o.setWorkerId(WORKER_ID);
        o.setAssignedAt(java.time.LocalDateTime.now());
        o.setAcceptedAt(java.time.LocalDateTime.now());
        o.setDueAt(java.time.LocalDateTime.now());
        fire(o, OrderAction.TRANSFER, Role.REPAIRER, WORKER_ID, "需专业设备", null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getWorkerId()).isNull();
        assertThat(o.getAssignedAt()).isNull();
        assertThat(o.getAcceptedAt()).isNull();
        assertThat(o.getDueAt()).isNull();
    }

    // ---------- #13 评价 ----------

    @Test
    @DisplayName("#13 评价：已完成→已评价（仅本人）")
    void rate() {
        RepairOrder o = order(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> fire(o, OrderAction.RATE, Role.REPORTER, OTHER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40301);
        fire(o, OrderAction.RATE, Role.REPORTER, REPORTER_ID, null, null);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.EVALUATED);
        assertThat(o.getRatedAt()).isNotNull();
    }

    // ---------- 非法转移抽查 ----------

    @Test
    @DisplayName("非法转移：待审核直接接单 / 已完成重复完工 / 已评价再评价 → 40901")
    void illegalTransitions() {
        assertThatThrownBy(() -> fire(order(OrderStatus.PENDING_AUDIT), OrderAction.ACCEPT,
                Role.REPAIRER, WORKER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40901);
        assertThatThrownBy(() -> fire(order(OrderStatus.COMPLETED), OrderAction.FINISH,
                Role.REPAIRER, WORKER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40901);
        assertThatThrownBy(() -> fire(order(OrderStatus.EVALUATED), OrderAction.RATE,
                Role.REPORTER, REPORTER_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(40901);
    }
}
