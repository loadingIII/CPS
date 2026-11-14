package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.repair.module.notice.NotificationService;
import com.campus.repair.module.order.statemachine.OrderAction;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.worker.WorkerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 派单与工单的定时兜底任务（无 Redis / MQ 依赖，单实例即可）。
 *
 * <ul>
 *   <li>接单超时回收：已派待接超过 assign_expires_at → 回池、拒绝计数 +1、立即开放抢单</li>
 *   <li>处理超时标记：处理中超过 due_at → is_overdue=1 + 预警日志</li>
 *   <li>默认好评：完工超过 N 天未评价 → 自动 5 星关闭</li>
 * </ul>
 *
 * <p>所有更新都用「版本号 + 状态 + 承接人」条件更新，避免把并发接单/评价的工单改回去。
 */
@Component
public class DispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchScheduler.class);
    private static final int BATCH = 200;

    private final RepairOrderMapper orderMapper;
    private final EvaluationMapper evaluationMapper;
    private final WorkerMapper workerMapper;
    private final OrderLogService logService;
    private final DispatchProperties props;
    private final OverdueAlertMapper alertMapper;
    private final NotificationService notificationService;

    public DispatchScheduler(RepairOrderMapper orderMapper, EvaluationMapper evaluationMapper,
                             WorkerMapper workerMapper, OrderLogService logService,
                             DispatchProperties props, OverdueAlertMapper alertMapper,
                             NotificationService notificationService) {
        this.orderMapper = orderMapper;
        this.evaluationMapper = evaluationMapper;
        this.workerMapper = workerMapper;
        this.logService = logService;
        this.props = props;
        this.alertMapper = alertMapper;
        this.notificationService = notificationService;
    }

    /** 接单超时自动回收 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    @Transactional
    public void recycleExpiredOffers() {
        LocalDateTime now = LocalDateTime.now();
        List<RepairOrder> expired = orderMapper.selectExpiredOffers(now, BATCH);
        int recycled = 0;
        for (RepairOrder o : expired) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, o.getId())
                    .eq(RepairOrder::getVersion, o.getVersion())
                    .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                    .eq(RepairOrder::getWorkerId, o.getWorkerId())
                    .set(RepairOrder::getWorkerId, null)
                    .set(RepairOrder::getAssignedAt, null)
                    .set(RepairOrder::getAcceptedAt, null)
                    .set(RepairOrder::getAssignExpiresAt, null)
                    .set(RepairOrder::getDueAt, null)
                    .set(RepairOrder::getGrabOpenAt, now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                    .set(RepairOrder::getRejectCount, nvl(o.getRejectCount()) + 1)
                    .set(RepairOrder::getVersion, o.getVersion() + 1));
            if (rows == 1) {
                recycled++;
                logService.log(o.getId(), OrderAction.RECYCLE, o.getWorkerId(), null,
                        "接单超时自动回收");
                raiseAlert(o, "ACCEPT", "接单超时自动回收，工单 " + o.getOrderNo() + " 已回待派单池");
            }
            // rows == 0：师傅刚好接单/工单被改派，跳过即可
        }
        if (recycled > 0) {
            log.info("[Dispatch] 接单超时回收 {} 张工单", recycled);
        }
    }

    /** 处理超时标记（超时是标记位，不是独立状态） */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    @Transactional
    public void markOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<RepairOrder> overdue = orderMapper.selectOverdueProcessing(now, BATCH);
        for (RepairOrder o : overdue) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, o.getId())
                    .eq(RepairOrder::getVersion, o.getVersion())
                    .eq(RepairOrder::getStatus, OrderStatus.PROCESSING)
                    .set(RepairOrder::getIsOverdue, true)
                    .set(RepairOrder::getVersion, o.getVersion() + 1));
            if (rows == 1) {
                logService.log(o.getId(), OrderAction.OVERDUE, o.getWorkerId(), null,
                        "处理超时，已超过处理截止时间");
                raiseAlert(o, "PROCESS", "工单 " + o.getOrderNo() + " 处理超时（" + o.getLocationText() + "）");
            }
        }
    }

    /** 完工 N 天未评价 → 默认好评关闭 */
    @Scheduled(cron = "0 15 3 * * ?")
    @Transactional
    public void autoRateCompleted() {
        LocalDateTime before = LocalDateTime.now().minusDays(props.getAutoRateDays());
        List<RepairOrder> list = orderMapper.selectCompletedBefore(before, BATCH);
        for (RepairOrder o : list) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, o.getId())
                    .eq(RepairOrder::getVersion, o.getVersion())
                    .eq(RepairOrder::getStatus, OrderStatus.COMPLETED)
                    .set(RepairOrder::getStatus, OrderStatus.EVALUATED)
                    .set(RepairOrder::getRatedAt, LocalDateTime.now())
                    .set(RepairOrder::getVersion, o.getVersion() + 1));
            if (rows != 1) {
                continue;
            }
            Evaluation e = new Evaluation();
            e.setOrderId(o.getId());
            e.setRating(5);
            e.setContent("系统默认好评（完工超过 " + props.getAutoRateDays() + " 天未评价）");
            e.setIsDefault(true);
            try {
                evaluationMapper.insert(e);
            } catch (DuplicateKeyException ignored) {
                // 报修人刚好提交了评价，唯一键兜底
            }
            if (o.getWorkerId() != null) {
                workerMapper.recalcScore(o.getWorkerId());
            }
            logService.log(o.getId(), OrderAction.DEFAULT_RATE, null, null,
                    "完工超过 " + props.getAutoRateDays() + " 天未评价，系统默认好评");
        }
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 写超时预警事件并通知相关人：同一工单重复超时按已有预警数升级。
     * 预警与工单标记位职责分离，便于统计"超时必预警"覆盖率。
     */
    private void raiseAlert(RepairOrder o, String stage, String content) {
        OverdueAlert alert = new OverdueAlert();
        alert.setOrderId(o.getId());
        alert.setStage(stage);
        alert.setLevel(Math.min(3, alertMapper.countByOrder(o.getId()) + 1));
        alert.setHandled(false);
        alert.setCreatedAt(LocalDateTime.now());
        alertMapper.insert(alert);
        notificationService.notifyAdmins(NotificationService.OVERDUE, content, o.getId());
        if (o.getWorkerId() != null) {
            notificationService.notifyUser(o.getWorkerId(), NotificationService.OVERDUE, content, o.getId());
        }
    }
}
