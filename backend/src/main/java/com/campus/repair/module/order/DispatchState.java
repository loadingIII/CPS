package com.campus.repair.module.order;

import com.campus.repair.module.order.statemachine.OrderStatus;

/**
 * 派单子状态（派生，不落库）。
 *
 * <p>工单主状态（待审核/待处理/处理中/已完成/已评价）描述业务生命周期；
 * 派单子状态描述「待处理」阶段内部的调度过程。二者正交：
 *
 * <pre>
 *   POOL     待派单：status=PENDING 且 worker_id IS NULL
 *   OFFERED  已派待接：status=PENDING 且 worker_id IS NOT NULL
 *   TAKEN    已承接：status ∈ {PROCESSING, COMPLETED, EVALUATED}
 * </pre>
 *
 * <p>不新增冗余状态列，避免与 worker_id 双写不一致；所有队列查询都以此为准。
 */
public enum DispatchState {

    NONE("—"),
    POOL("待派单"),
    OFFERED("已派待接"),
    TAKEN("已承接");

    private final String label;

    DispatchState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static DispatchState of(RepairOrder order) {
        if (order == null || order.getStatus() == null) {
            return NONE;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            return order.getWorkerId() == null ? NONE : TAKEN;
        }
        return order.getWorkerId() == null ? POOL : OFFERED;
    }
}
