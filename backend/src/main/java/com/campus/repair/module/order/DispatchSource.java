package com.campus.repair.module.order;

/**
 * 派单来源：区分人工派单、抢单与自动派单，支撑「抢单占比」「自动采纳率」统计。
 */
public enum DispatchSource {
    MANUAL("人工派单"),
    GRAB("抢单"),
    AUTO("自动派单");

    private final String label;

    DispatchSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
