package com.campus.repair.module.worker.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 派单看板维修工视图（含资格判定结果，§2.5）。
 */
public record EligibleWorkerVO(Long workerId, String name, List<String> skills, List<String> campuses,
                               Integer currentLoad, Integer loadLimit, Boolean onDuty, BigDecimal score,
                               boolean eligible, List<String> failReasons) {
}
