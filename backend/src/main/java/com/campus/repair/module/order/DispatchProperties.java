package com.campus.repair.module.order;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 派单调度参数（application.yml 中 dispatch.*）。
 *
 * <p>所有时限与权重都可配置，答辩时可直接改配置演示不同策略。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dispatch")
public class DispatchProperties {

    /** 推荐评分权重 */
    private Weights weights = new Weights();

    /** 派单后接单时限（分钟），key = NORMAL/URGENT */
    private Map<String, Integer> acceptTimeoutMinutes = new HashMap<>();

    /** 处理 SLA（小时），key = NORMAL/URGENT */
    private Map<String, Integer> slaHours = new HashMap<>();

    /** 审核通过后延迟多久开放抢单（分钟），key = NORMAL/URGENT */
    private Map<String, Integer> grabOpenDelayMinutes = new HashMap<>();

    /** 拒单/超时回收后的冷却时长（小时） */
    private int rejectCooldownHours = 24;

    /** 累计派单次数达到该值标记为难派单 */
    private int hardOrderThreshold = 3;

    /** 完工后多少天未评价自动默认好评 */
    private int autoRateDays = 7;

    @Data
    public static class Weights {
        private double skill = 0.40;
        private double capacity = 0.25;
        private double timeliness = 0.20;
        private double rating = 0.15;
    }

    public int acceptTimeoutMinutes(Urgency urgency) {
        return acceptTimeoutMinutes.getOrDefault(key(urgency), 120);
    }

    public int slaHours(Urgency urgency) {
        return slaHours.getOrDefault(key(urgency), 24);
    }

    public int grabOpenDelayMinutes(Urgency urgency) {
        return grabOpenDelayMinutes.getOrDefault(key(urgency), 20);
    }

    private String key(Urgency urgency) {
        return urgency == null ? Urgency.NORMAL.name() : urgency.name();
    }
}
