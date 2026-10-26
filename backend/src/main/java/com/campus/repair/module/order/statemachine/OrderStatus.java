package com.campus.repair.module.order.statemachine;

/**
 * 工单状态（对应 repair_order.status ENUM）。
 */
public enum OrderStatus {
    PENDING_AUDIT("待审核"),
    PENDING("待处理"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    EVALUATED("已评价"),
    REJECTED("已驳回"),
    CANCELLED("已取消");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
