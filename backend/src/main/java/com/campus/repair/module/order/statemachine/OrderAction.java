package com.campus.repair.module.order.statemachine;

/**
 * 工单动作（状态机触发事件，label 用于 order_log.action 展示）。
 */
public enum OrderAction {
    SUBMIT("提交"),
    RESUBMIT("补充重报"),
    AUDIT_PASS("审核通过"),
    AUDIT_REJECT("驳回"),
    CANCEL("取消"),
    ABANDON("放弃"),
    ASSIGN("派单"),
    CLAIM("抢单"),
    ACCEPT("接单"),
    REJECT_ASSIGN("拒单"),
    FINISH("完工"),
    TRANSFER("转单"),
    RATE("评价");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
