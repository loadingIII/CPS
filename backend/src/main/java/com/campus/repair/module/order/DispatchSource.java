package com.campus.repair.module.order;

/**
 * 派单来源：人工派单 / 自动派单。
 * <p>GRAB 仅为历史数据兼容保留（v2.6 起已移除抢单业务，不再产生新值）。
 */
public enum DispatchSource {
    MANUAL("人工派单"),
    GRAB("抢单（历史）"),
    AUTO("自动派单");

    private final String label;

    DispatchSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
