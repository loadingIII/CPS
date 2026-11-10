package com.campus.repair.module.order.dto;

import com.campus.repair.module.order.DispatchSource;
import com.campus.repair.module.order.DispatchState;

import java.time.LocalDateTime;

/**
 * 派单调度信息（前端倒计时、难派单提示、排查用）。
 *
 * @param hardOrder               累计派单次数达到阈值，标记为难派单
 * @param acceptRemainingSeconds  剩余接单时间（秒），无派单截止时间时为 0
 */
public record DispatchInfoVO(Long orderId, DispatchState dispatchState, DispatchSource dispatchSource,
                             LocalDateTime grabOpenAt, LocalDateTime assignExpiresAt, LocalDateTime dueAt,
                             Integer dispatchCount, Integer rejectCount,
                             boolean hardOrder, long acceptRemainingSeconds) {
}
