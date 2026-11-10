package com.campus.repair.module.worker.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 派单看板维修工视图（含资格判定与推荐评分）。
 *
 * @param eligible     是否满足全部硬约束
 * @param failReasons  不满足硬约束的原因
 * @param matchScore   推荐评分（0~1，仅 eligible 时有意义）
 * @param breakdown    各因子得分，便于前端展示与答辩讲解
 * @param matchReasons 推荐理由（自然语言短语）
 */
public record EligibleWorkerVO(Long workerId, String name, List<String> skills, List<String> campuses,
                               Integer currentLoad, Integer loadLimit, Boolean onDuty, BigDecimal score,
                               boolean eligible, List<String> failReasons,
                               Double matchScore, Map<String, Double> breakdown,
                               List<String> matchReasons) {
}
