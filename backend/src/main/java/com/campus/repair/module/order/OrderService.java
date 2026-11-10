package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.LoginUser;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.dict.Skill;
import com.campus.repair.module.dict.SkillMapper;
import com.campus.repair.module.order.dto.AssignReq;
import com.campus.repair.module.order.dto.AuditReq;
import com.campus.repair.module.order.dto.DispatchInfoVO;
import com.campus.repair.module.order.dto.EvaluationReq;
import com.campus.repair.module.order.dto.FinishReq;
import com.campus.repair.module.order.dto.MapOrderVO;
import com.campus.repair.module.order.dto.OrderCreatedVO;
import com.campus.repair.module.order.dto.ReasonReq;
import com.campus.repair.module.order.dto.ResubmitReq;
import com.campus.repair.module.order.dto.SubmitReq;
import com.campus.repair.module.order.statemachine.OrderAction;
import com.campus.repair.module.order.statemachine.OrderStateMachine;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.worker.WorkerMapper;
import com.campus.repair.module.worker.WorkerProfile;
import com.campus.repair.module.worker.WorkerProfileMapper;
import com.campus.repair.module.worker.WorkerService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final RepairOrderMapper orderMapper;
    private final AttachmentMapper attachmentMapper;
    private final OrderLogMapper logMapper;
    private final EvaluationMapper evaluationMapper;
    private final OrderStateMachine stateMachine;
    private final OrderLogService logService;
    private final SkillMapper skillMapper;
    private final UserMapper userMapper;
    private final WorkerMapper workerMapper;
    private final WorkerService workerService;
    private final WorkerProfileMapper profileMapper;
    private final IdempotentStore idempotentStore;
    private final DispatchProperties props;

    public OrderService(RepairOrderMapper orderMapper, AttachmentMapper attachmentMapper,
                        OrderLogMapper logMapper, EvaluationMapper evaluationMapper,
                        OrderStateMachine stateMachine, OrderLogService logService,
                        SkillMapper skillMapper, UserMapper userMapper,
                        WorkerMapper workerMapper, WorkerService workerService,
                        WorkerProfileMapper profileMapper,
                        IdempotentStore idempotentStore, DispatchProperties props) {
        this.orderMapper = orderMapper;
        this.attachmentMapper = attachmentMapper;
        this.logMapper = logMapper;
        this.evaluationMapper = evaluationMapper;
        this.stateMachine = stateMachine;
        this.logService = logService;
        this.skillMapper = skillMapper;
        this.userMapper = userMapper;
        this.workerMapper = workerMapper;
        this.workerService = workerService;
        this.profileMapper = profileMapper;
        this.idempotentStore = idempotentStore;
        this.props = props;
    }

    // ==================================================================
    // F2 报修提交
    // ==================================================================

    @Transactional
    public OrderCreatedVO submit(SubmitReq req) {
        if (StringUtils.hasText(req.clientToken())) {
            return idempotentStore.computeIfAbsent("order:token:" + req.clientToken(),
                    Duration.ofMinutes(5), () -> doSubmit(req));
        }
        return doSubmit(req);
    }

    private OrderCreatedVO doSubmit(SubmitReq req) {
        Long uid = AuthContext.userId();

        Campus campus = Campus.of(req.campus());
        if (campus == null) {
            throw BusinessException.param("校区无效");
        }
        Long skillCount = skillMapper.selectCount(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getName, req.faultType())
                .eq(Skill::getEnabled, true));
        if (skillCount == null || skillCount == 0) {
            throw BusinessException.param("故障类型无效: " + req.faultType());
        }

        RepairOrder o = new RepairOrder();
        o.setReporterId(uid);
        o.setCampus(campus.name());
        o.setLocationText(Campus.format(campus.name(), req.locationDetail()));
        // 地图选点补充定位（选填，DB-02）：范围校验后保存
        if (req.mapLng() != null && req.mapLat() != null) {
            if (req.mapLng() < -180 || req.mapLng() > 180 || req.mapLat() < -90 || req.mapLat() > 90) {
                throw BusinessException.param("地图选点坐标超出范围");
            }
            o.setMapName(StringUtils.hasText(req.mapName()) ? req.mapName() : "地图选点");
            o.setMapLng(req.mapLng());
            o.setMapLat(req.mapLat());
        }
        o.setFaultType(req.faultType());
        o.setUrgency(req.urgency());
        o.setDescription(req.description());
        o.setStatus(OrderStatus.PENDING_AUDIT);
        o.setIsOverdue(false);
        o.setVersion(0);
        o.setDispatchCount(0);
        o.setRejectCount(0);
        o.setSubmittedAt(LocalDateTime.now());

        // 工单号：BX+yyyyMMdd+4位序号，唯一键冲突重试（§2.3，Redis 降级为查库 max+1）
        String prefix = "BX" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int attempt = 1; attempt <= 3; attempt++) {
            int seq = orderMapper.selectMaxSeqToday(prefix) + 1;
            if (seq > 9999) {
                throw new BusinessException(com.campus.repair.common.ErrorCode.SYSTEM_ERROR, "当日工单量已达上限");
            }
            o.setOrderNo(prefix + String.format("%04d", seq));
            try {
                orderMapper.insert(o);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == 3) {
                    throw new BusinessException(com.campus.repair.common.ErrorCode.SYSTEM_ERROR, "工单号生成冲突，请重试");
                }
            }
        }
        saveAttachments(o.getId(), req.imageUrls(), AttachmentType.REPORT);
        logService.log(o.getId(), OrderAction.SUBMIT, uid, AuthContext.role(), null);
        return new OrderCreatedVO(o.getId(), o.getOrderNo());
    }

    @Transactional
    public void resubmit(Long orderId, ResubmitReq req) {
        RepairOrder o = load(orderId);
        o.setDescription(req.description());
        attachmentMapper.delete(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOrderId, orderId)
                .eq(Attachment::getType, AttachmentType.REPORT));
        transition(o, OrderAction.RESUBMIT, null, null);
        saveAttachments(orderId, req.imageUrls(), AttachmentType.REPORT);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** 我的报修（F6-2） */
    public PageVO<OrderListVO> pageMy(OrderStatus status, long page, long pageSize) {
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getReporterId, AuthContext.userId())
                        .eq(status != null, RepairOrder::getStatus, status)
                        .orderByDesc(RepairOrder::getSubmittedAt));
        return new PageVO<>(toListVOs(p.getRecords()), p.getTotal());
    }

    /**
     * 管理视角工单列表（F3-1 审核队列复用）。
     * dispatchState 用于把「待派单」与「已派待接」拆成两个队列。
     */
    public PageVO<OrderListVO> pageAdmin(OrderStatus status, Urgency urgency, String faultType,
                                         String keyword, DispatchState dispatchState,
                                         long page, long pageSize) {
        LambdaQueryWrapper<RepairOrder> w = new LambdaQueryWrapper<RepairOrder>()
                .eq(status != null, RepairOrder::getStatus, status)
                .eq(urgency != null, RepairOrder::getUrgency, urgency)
                .eq(StringUtils.hasText(faultType), RepairOrder::getFaultType, faultType);
        if (dispatchState != null) {
            switch (dispatchState) {
                case POOL -> w.eq(RepairOrder::getStatus, OrderStatus.PENDING)
                        .isNull(RepairOrder::getWorkerId);
                case OFFERED -> w.eq(RepairOrder::getStatus, OrderStatus.PENDING)
                        .isNotNull(RepairOrder::getWorkerId);
                case TAKEN -> w.isNotNull(RepairOrder::getWorkerId);
                default -> { }
            }
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> reporterIds = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getName, keyword))
                    .stream().map(SysUser::getId).toList();
            w.and(x -> {
                x.like(RepairOrder::getOrderNo, keyword)
                        .or().like(RepairOrder::getLocationText, keyword);
                if (!reporterIds.isEmpty()) {
                    x.or().in(RepairOrder::getReporterId, reporterIds);
                }
            });
        }
        if (dispatchState == DispatchState.OFFERED) {
            w.orderByAsc(RepairOrder::getAssignExpiresAt);
        } else if (status == OrderStatus.PENDING_AUDIT || status == OrderStatus.PENDING) {
            w.orderByDesc(RepairOrder::getUrgency).orderByAsc(RepairOrder::getSubmittedAt);
        } else {
            w.orderByDesc(RepairOrder::getSubmittedAt);
        }
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), w);
        return new PageVO<>(toListVOs(p.getRecords()), p.getTotal());
    }

    /** 管理工作台统计：全量状态、近 7 日趋势与近 30 日故障类型分布。 */
    public OrderStatisticsVO statistics() {
        List<Map<String, Object>> statusRows = orderMapper.countByStatus();
        List<OrderStatisticsVO.StatusCount> statuses = statusRows.stream()
                .map(row -> new OrderStatisticsVO.StatusCount(
                        String.valueOf(row.get("status")), countValue(row.get("total"))))
                .toList();
        long total = statuses.stream().mapToLong(OrderStatisticsVO.StatusCount::count).sum();

        LocalDate today = LocalDate.now();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        Map<String, Long> dailyCounts = orderMapper.countBySubmittedDay(weekStart, tomorrow).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("day")),
                        row -> countValue(row.get("total"))));
        List<OrderStatisticsVO.DailyCount> daily = new ArrayList<>(7);
        for (int offset = 6; offset >= 0; offset--) {
            String day = today.minusDays(offset).toString();
            daily.add(new OrderStatisticsVO.DailyCount(day, dailyCounts.getOrDefault(day, 0L)));
        }

        LocalDateTime monthStart = today.minusDays(29).atStartOfDay();
        List<OrderStatisticsVO.FaultTypeCount> faultTypes = orderMapper.countByFaultType(monthStart, tomorrow).stream()
                .map(row -> new OrderStatisticsVO.FaultTypeCount(
                        row.get("faultType") == null ? "未分类" : String.valueOf(row.get("faultType")),
                        countValue(row.get("total"))))
                .toList();

        return new OrderStatisticsVO(total, statuses, daily, faultTypes);
    }

    private long countValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 维修工：我的工单（F5-1）。
     * 已完成页签同时返回 EVALUATED，避免评价后记录从师傅列表消失。
     */
    public PageVO<OrderListVO> mineAssigned(OrderStatus status, long page, long pageSize) {
        OrderStatus st = status == null ? OrderStatus.PENDING : status;
        LambdaQueryWrapper<RepairOrder> w = new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getWorkerId, AuthContext.userId());
        if (st == OrderStatus.COMPLETED) {
            w.in(RepairOrder::getStatus, OrderStatus.COMPLETED, OrderStatus.EVALUATED);
        } else {
            w.eq(RepairOrder::getStatus, st);
        }
        if (st == OrderStatus.PENDING) {
            w.orderByDesc(RepairOrder::getUrgency).orderByAsc(RepairOrder::getAssignedAt);
        } else if (st == OrderStatus.COMPLETED) {
            w.orderByDesc(RepairOrder::getCompletedAt);
        } else {
            w.orderByDesc(RepairOrder::getSubmittedAt);
        }
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), w);
        return new PageVO<>(toListVOs(p.getRecords()), p.getTotal());
    }

    /** 工单详情（本人相关单/管理员/抢单预览） */
    public OrderDetailVO detail(Long orderId) {
        RepairOrder o = load(orderId);
        checkReadPermission(o);
        return buildDetail(o);
    }

    /** 状态时间线（F6-1） */
    public List<OrderLogVO> logs(Long orderId) {
        RepairOrder o = load(orderId);
        checkReadPermission(o);
        return logMapper.selectByOrderId(orderId);
    }

    // ==================================================================
    // 状态机动作
    // ==================================================================

    @Transactional
    public void audit(Long orderId, AuditReq req) {
        RepairOrder o = load(orderId);
        boolean pass = Boolean.TRUE.equals(req.pass());
        if (pass) {
            // 审核通过即确定抢单开放时间：紧急单立即，普通单留给管理员一个定向派单窗口。
            // 立即开放必须截断到秒：DATETIME(0) 会把毫秒四舍五入，可能把「现在」推到未来。
            int delay = props.grabOpenDelayMinutes(o.getUrgency());
            LocalDateTime openAt = LocalDateTime.now().plusMinutes(delay);
            if (delay <= 0) {
                openAt = openAt.truncatedTo(ChronoUnit.SECONDS);
            }
            o.setGrabOpenAt(openAt);
        }
        transition(o, pass ? OrderAction.AUDIT_PASS : OrderAction.AUDIT_REJECT,
                pass ? null : req.reason(), null);
    }

    @Transactional
    public void cancel(Long orderId) {
        transition(load(orderId), OrderAction.CANCEL, null, null);
    }

    @Transactional
    public void abandon(Long orderId) {
        transition(load(orderId), OrderAction.ABANDON, null, null);
    }

    public List<com.campus.repair.module.worker.dto.EligibleWorkerVO> eligibleWorkers(Long orderId) {
        return workerService.eligibleWorkersForOrder(load(orderId));
    }

    /**
     * 手动派单（F4-2，支持改派）。
     *
     * <p>并发安全：先锁目标师傅的容量行（worker_profile FOR UPDATE），再校验负载、条件更新工单。
     * 锁序固定 worker → order，事务内只锁一个师傅，避免死锁。
     */
    @Transactional
    public void assign(Long orderId, AssignReq req) {
        WorkerProfile target = profileMapper.selectForUpdate(req.workerId());
        if (target == null) {
            throw BusinessException.param("所选维修工不存在或未建立档案");
        }

        RepairOrder o = load(orderId);
        if (o.getStatus() != OrderStatus.PENDING) {
            throw BusinessException.conflict("工单当前状态[" + o.getStatus().label() + "]不允许派单");
        }
        if (req.workerId().equals(o.getWorkerId())) {
            return; // 重复派给同一人：幂等，不重置接单倒计时
        }
        boolean reassign = o.getWorkerId() != null;
        if (reassign && !StringUtils.hasText(req.reason())) {
            throw BusinessException.param("改派必须填写理由");
        }

        List<String> reasons = workerService.failReasons(req.workerId(), o, false);
        if (!reasons.isEmpty()) {
            throw BusinessException.param("该维修工不可派单：" + String.join("；", reasons));
        }
        long load = orderMapper.countActiveByWorker(req.workerId(), orderId);
        if (load >= target.getLoadLimit()) {
            throw BusinessException.conflict("负载已满（" + load + "/" + target.getLoadLimit() + "）");
        }

        String remark = reassign
                ? "改派自 " + workerName(o.getWorkerId())
                    + (StringUtils.hasText(req.reason()) ? "：" + req.reason() : "")
                : "派给 " + workerName(req.workerId())
                    + (StringUtils.hasText(req.reason()) ? "：" + req.reason() : "");

        o.setDispatchSource(DispatchSource.MANUAL);
        o.setDispatchCount(nvl(o.getDispatchCount()) + 1);
        o.setAssignExpiresAt(LocalDateTime.now()
                .plusMinutes(props.acceptTimeoutMinutes(o.getUrgency())));
        transition(o, OrderAction.ASSIGN, remark, req.workerId());
    }

    @Transactional
    public void accept(Long orderId) {
        RepairOrder o = load(orderId);
        o.setDueAt(LocalDateTime.now().plusHours(props.slaHours(o.getUrgency())));
        transition(o, OrderAction.ACCEPT, null, null);
    }

    /**
     * 抢单大厅：资格（技能/校区/开放时间）在 SQL 层过滤，分页与总数同条件。
     * 只返回当前维修工真正能抢的工单，避免「整页空白」与 total 错乱。
     */
    public PageVO<OrderListVO> grabPool(long page, long pageSize) {
        Long uid = AuthContext.userId();
        long size = Math.max(1, Math.min(pageSize, 50));
        long offset = (Math.max(page, 1) - 1) * size;
        LocalDateTime now = LocalDateTime.now();

        List<RepairOrder> orders = orderMapper.selectGrabPool(uid, now, offset, size);
        long total = orderMapper.countGrabPool(uid, now);
        List<OrderListVO> vos = toListVOs(orders);
        workerService.fillGrabMatch(vos, orders);
        return new PageVO<>(vos, total);
    }

    /** 维修工工单地图：已接单（待接/在办）+ 可抢单的坐标点 */
    public List<MapOrderVO> workerMap() {
        Long uid = AuthContext.userId();
        List<RepairOrder> mine = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getWorkerId, uid)
                .in(RepairOrder::getStatus, OrderStatus.PENDING, OrderStatus.PROCESSING)
                .orderByDesc(RepairOrder::getUrgency));
        List<RepairOrder> pool = orderMapper.selectGrabPool(uid, LocalDateTime.now(), 0, 200);

        List<MapOrderVO> result = new ArrayList<>();
        for (RepairOrder o : mine) {
            addMapPoint(result, o, "MINE");
        }
        for (RepairOrder o : pool) {
            addMapPoint(result, o, "GRAB");
        }
        return result;
    }

    /** 组装地图点：工单自带选点坐标优先，否则回退校区默认中心坐标；无有效校区则跳过 */
    private void addMapPoint(List<MapOrderVO> list, RepairOrder o, String kind) {
        Double lng = o.getMapLng();
        Double lat = o.getMapLat();
        if (lng == null || lat == null) {
            Map<String, Object> geo = Campus.geo(o.getCampus());
            if (geo == null) {
                return;
            }
            lng = ((Number) geo.get("lng")).doubleValue();
            lat = ((Number) geo.get("lat")).doubleValue();
        }
        list.add(new MapOrderVO(o.getId(), o.getOrderNo(), o.getStatus().label(),
                o.getUrgency().name(), o.getLocationText(), o.getFaultType(),
                o.getMapName(), lng, lat, kind));
    }

    /**
     * 抢单：抢到即接单（PENDING → PROCESSING）。
     * 容量行加锁 + 工单条件更新（version + worker_id IS NULL），先到先得。
     */
    @Transactional
    public void claim(Long orderId) {
        Long uid = AuthContext.userId();
        WorkerProfile profile = profileMapper.selectForUpdate(uid);
        if (profile == null) {
            throw BusinessException.param("缺少维修工档案");
        }
        if (!Boolean.TRUE.equals(profile.getOnDuty())) {
            throw BusinessException.param("当前不在岗，无法抢单");
        }

        RepairOrder o = load(orderId);
        if (o.getStatus() != OrderStatus.PENDING) {
            throw BusinessException.conflict("工单当前状态[" + o.getStatus().label() + "]不允许抢单");
        }
        if (o.getWorkerId() != null) {
            throw BusinessException.conflict("手慢了，该工单已被其他师傅承接");
        }
        if (o.getGrabOpenAt() == null || o.getGrabOpenAt().isAfter(LocalDateTime.now())) {
            throw BusinessException.conflict("该工单尚未开放抢单");
        }

        List<String> reasons = workerService.failReasons(uid, o, false);
        if (!reasons.isEmpty()) {
            throw BusinessException.param("暂不满足承接条件：" + String.join("；", reasons));
        }
        long load = orderMapper.countActiveByWorker(uid, null);
        if (load >= profile.getLoadLimit()) {
            throw BusinessException.conflict("负载已满（" + load + "/" + profile.getLoadLimit() + "）");
        }

        LocalDateTime now = LocalDateTime.now();
        o.setAcceptedAt(now);
        o.setDueAt(now.plusHours(props.slaHours(o.getUrgency())));
        o.setDispatchSource(DispatchSource.GRAB);
        o.setDispatchCount(nvl(o.getDispatchCount()) + 1);
        o.setAssignExpiresAt(null);
        transition(o, OrderAction.CLAIM, "维修工抢单自助承接", uid);
    }

    @Transactional
    public void rejectAssign(Long orderId, ReasonReq req) {
        RepairOrder o = load(orderId);
        o.setRejectCount(nvl(o.getRejectCount()) + 1);
        // 回流后立即开放抢单，不再让难单在池子里干等
        o.setGrabOpenAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        transition(o, OrderAction.REJECT_ASSIGN, req.reason(), null);
    }

    @Transactional
    public void finish(Long orderId, FinishReq req) {
        RepairOrder o = load(orderId);
        o.setFinishSummary(req.summary());
        transition(o, OrderAction.FINISH, req.summary(), null);
        saveAttachments(orderId, req.finishImageUrls(), AttachmentType.FINISH);
    }

    @Transactional
    public void transfer(Long orderId, ReasonReq req) {
        RepairOrder o = load(orderId);
        o.setGrabOpenAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        transition(o, OrderAction.TRANSFER, req.reason(), null);
    }

    @Transactional
    public void evaluate(Long orderId, EvaluationReq req) {
        RepairOrder o = load(orderId);
        Long exists = evaluationMapper.selectCount(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, orderId));
        if (exists != null && exists > 0) {
            throw BusinessException.conflict("该工单已评价");
        }
        transition(o, OrderAction.RATE, null, null);
        Evaluation e = new Evaluation();
        e.setOrderId(orderId);
        e.setRating(req.rating());
        e.setContent(req.content());
        e.setTags(req.tags() == null || req.tags().isEmpty() ? null : String.join(",", req.tags()));
        e.setIsDefault(false);
        evaluationMapper.insert(e);
        // 更新维修工综合评分（§2.6）
        if (o.getWorkerId() != null) {
            workerMapper.recalcScore(o.getWorkerId());
        }
    }

    public Evaluation getEvaluation(Long orderId) {
        RepairOrder o = load(orderId);
        checkReadPermission(o);
        return evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, orderId));
    }

    /** 派单调度信息：子状态、来源、次数、剩余接单时间 */
    public DispatchInfoVO dispatchInfo(Long orderId) {
        RepairOrder o = load(orderId);
        checkReadPermission(o);
        long remain = 0;
        if (o.getAssignExpiresAt() != null) {
            remain = Math.max(0, Duration.between(LocalDateTime.now(), o.getAssignExpiresAt()).getSeconds());
        }
        return new DispatchInfoVO(
                o.getId(),
                DispatchState.of(o),
                o.getDispatchSource(),
                o.getGrabOpenAt(),
                o.getAssignExpiresAt(),
                o.getDueAt(),
                nvl(o.getDispatchCount()),
                nvl(o.getRejectCount()),
                nvl(o.getDispatchCount()) >= props.getHardOrderThreshold(),
                remain);
    }

    /** 管理员手动开放抢单（应对难派单） */
    @Transactional
    public void broadcast(Long orderId) {
        RepairOrder o = load(orderId);
        if (o.getStatus() != OrderStatus.PENDING || o.getWorkerId() != null) {
            throw BusinessException.conflict("只有待派单工单可以开放抢单");
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .eq(RepairOrder::getVersion, o.getVersion())
                .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                .isNull(RepairOrder::getWorkerId)
                .set(RepairOrder::getGrabOpenAt, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS))
                .set(RepairOrder::getVersion, o.getVersion() + 1));
        if (rows != 1) {
            throw BusinessException.conflict("工单状态已变化，请刷新后重试");
        }
        logService.log(orderId, OrderAction.BROADCAST, AuthContext.userId(), AuthContext.role(),
                "管理员开放抢单");
    }

    // ==================================================================
    // 内部：乐观锁状态转移 + 时间线（§2.2 实现硬性要求）
    // ==================================================================

    /**
     * 校验 + 条件更新（版本号乐观锁）+ 同事务写 order_log；失败抛 40901。
     *
     * <p>所有可变字段都从内存实体整体写回（含 null），解决「回池时清不掉时间戳」的问题；
     * 并发安全由 version + 状态条件保证，CLAIM/ACCEPT 再叠加业务条件。
     */
    private RepairOrder transition(RepairOrder o, OrderAction action, String remark, Long extraWorkerId) {
        LoginUser op = AuthContext.get();
        OrderStatus beforeStatus = o.getStatus();
        Integer beforeVersion = o.getVersion();

        stateMachine.fire(o, action, op.role(), op.userId(), remark, extraWorkerId);

        int newVersion = beforeVersion + 1;
        o.setVersion(newVersion);
        LambdaUpdateWrapper<RepairOrder> w = new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, o.getId())
                .eq(RepairOrder::getVersion, beforeVersion)
                .eq(RepairOrder::getStatus, beforeStatus)
                .set(RepairOrder::getStatus, o.getStatus())
                .set(RepairOrder::getVersion, newVersion)
                .set(RepairOrder::getWorkerId, o.getWorkerId())
                .set(RepairOrder::getDescription, o.getDescription())
                .set(RepairOrder::getAuditedAt, o.getAuditedAt())
                .set(RepairOrder::getAssignedAt, o.getAssignedAt())
                .set(RepairOrder::getAcceptedAt, o.getAcceptedAt())
                .set(RepairOrder::getCompletedAt, o.getCompletedAt())
                .set(RepairOrder::getRatedAt, o.getRatedAt())
                .set(RepairOrder::getFinishSummary, o.getFinishSummary())
                .set(RepairOrder::getDispatchSource, o.getDispatchSource())
                .set(RepairOrder::getGrabOpenAt, o.getGrabOpenAt())
                .set(RepairOrder::getAssignExpiresAt, o.getAssignExpiresAt())
                .set(RepairOrder::getDueAt, o.getDueAt())
                .set(RepairOrder::getDispatchCount, o.getDispatchCount())
                .set(RepairOrder::getRejectCount, o.getRejectCount());
        switch (action) {
            // 抢单并发：必须仍为未派单（先到先得）
            case CLAIM -> w.isNull(RepairOrder::getWorkerId);
            // 并发接单幂等：必须仍是被派给本人
            case ACCEPT -> w.eq(RepairOrder::getWorkerId, op.userId());
            default -> { }
        }
        int rows = orderMapper.update(null, w);
        if (rows != 1) {
            throw BusinessException.conflict("工单状态已变化，请刷新后重试");
        }
        logService.log(o.getId(), action, op.userId(), op.role(), remark);
        return o;
    }

    private RepairOrder load(Long orderId) {
        RepairOrder o = orderMapper.selectById(orderId);
        if (o == null) {
            throw BusinessException.notFound("工单不存在");
        }
        return o;
    }

    /**
     * 详情/时间线读权限：报修人本人、被派维修工、管理员；
     * 维修工额外可预览「已开放抢单且自己有资格」的待派单工单（电话脱敏）。
     */
    private void checkReadPermission(RepairOrder o) {
        LoginUser u = AuthContext.get();
        switch (u.role()) {
            case ADMIN, SYS_ADMIN -> { }
            case REPORTER -> {
                if (!u.userId().equals(o.getReporterId())) {
                    throw BusinessException.notFound("工单不存在");
                }
            }
            case REPAIRER -> {
                if (u.userId().equals(o.getWorkerId())) {
                    return;
                }
                boolean grabPreview = o.getStatus() == OrderStatus.PENDING
                        && o.getWorkerId() == null
                        && o.getGrabOpenAt() != null
                        && !o.getGrabOpenAt().isAfter(LocalDateTime.now());
                if (grabPreview && workerService.canPreview(u.userId(), o)) {
                    return;
                }
                throw BusinessException.notFound("工单不存在");
            }
        }
    }

    private void saveAttachments(Long orderId, List<String> urls, AttachmentType type) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        for (String url : urls) {
            Attachment a = new Attachment();
            a.setOrderId(orderId);
            a.setUrl(url);
            a.setType(type);
            attachmentMapper.insert(a);
        }
    }

    private String workerName(Long workerId) {
        SysUser u = userMapper.selectById(workerId);
        return u == null ? String.valueOf(workerId) : u.getName();
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================================================================
    // VO 组装
    // ==================================================================

    private OrderListVO toListVO(RepairOrder o) {
        OrderListVO v = new OrderListVO();
        v.setId(o.getId());
        v.setOrderNo(o.getOrderNo());
        v.setLocationText(o.getLocationText());
        v.setFaultType(o.getFaultType());
        v.setUrgency(o.getUrgency());
        v.setStatus(o.getStatus());
        v.setIsOverdue(o.getIsOverdue());
        v.setDescription(o.getDescription());
        v.setSubmittedAt(o.getSubmittedAt());
        v.setWorkerId(o.getWorkerId());
        v.setDispatchState(DispatchState.of(o));
        v.setGrabOpenAt(o.getGrabOpenAt());
        v.setAssignExpiresAt(o.getAssignExpiresAt());
        v.setDispatchCount(o.getDispatchCount());
        v.setRejectCount(o.getRejectCount());
        return v;
    }

    /** 批量组装列表 VO 并补充报修人/维修工姓名 */
    private List<OrderListVO> toListVOs(List<RepairOrder> orders) {
        Set<Long> uids = new HashSet<>();
        for (RepairOrder o : orders) {
            uids.add(o.getReporterId());
            if (o.getWorkerId() != null) {
                uids.add(o.getWorkerId());
            }
        }
        Map<Long, String> names = uids.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(uids).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getName));
        return orders.stream().map(o -> {
            OrderListVO v = toListVO(o);
            v.setReporterName(names.get(o.getReporterId()));
            if (o.getWorkerId() != null) {
                v.setWorkerName(names.get(o.getWorkerId()));
            }
            return v;
        }).toList();
    }

    private OrderDetailVO buildDetail(RepairOrder o) {
        LoginUser u = AuthContext.get();
        // 抢单预览（未承接）时不暴露报修人电话
        boolean preview = u.role() == Role.REPAIRER && o.getWorkerId() == null;

        OrderDetailVO v = new OrderDetailVO();
        v.setId(o.getId());
        v.setOrderNo(o.getOrderNo());
        v.setReporterId(o.getReporterId());
        v.setWorkerId(o.getWorkerId());
        v.setCampus(o.getCampus());
        v.setCampusName(Campus.labelOf(o.getCampus()));
        v.setLocationText(o.getLocationText());
        // 导航坐标：报修时地图选点优先，否则回退到校区默认中心坐标
        if (o.getMapLng() != null && o.getMapLat() != null) {
            v.setLocationLng(o.getMapLng());
            v.setLocationLat(o.getMapLat());
            v.setMapName(o.getMapName());
        } else {
            Map<String, Object> geo = Campus.geo(o.getCampus());
            if (geo != null) {
                v.setLocationLng(((Number) geo.get("lng")).doubleValue());
                v.setLocationLat(((Number) geo.get("lat")).doubleValue());
            }
        }
        v.setFaultType(o.getFaultType());
        v.setUrgency(o.getUrgency());
        v.setDescription(o.getDescription());
        v.setStatus(o.getStatus());
        v.setIsOverdue(o.getIsOverdue());
        v.setSubmittedAt(o.getSubmittedAt());
        v.setAuditedAt(o.getAuditedAt());
        v.setAssignedAt(o.getAssignedAt());
        v.setAcceptedAt(o.getAcceptedAt());
        v.setCompletedAt(o.getCompletedAt());
        v.setRatedAt(o.getRatedAt());

        v.setFinishSummary(o.getFinishSummary());
        v.setDispatchState(DispatchState.of(o));
        v.setDispatchSource(o.getDispatchSource());
        v.setGrabOpenAt(o.getGrabOpenAt());
        v.setAssignExpiresAt(o.getAssignExpiresAt());
        v.setDueAt(o.getDueAt());
        v.setDispatchCount(o.getDispatchCount());
        v.setRejectCount(o.getRejectCount());

        List<Long> uids = new ArrayList<>();
        uids.add(o.getReporterId());
        if (o.getWorkerId() != null) {
            uids.add(o.getWorkerId());
        }
        Map<Long, SysUser> users = userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        SysUser reporter = users.get(o.getReporterId());
        if (reporter != null) {
            v.setReporterName(reporter.getName());
            if (!preview) {
                v.setReporterPhone(reporter.getPhone());
            }
        }
        if (o.getWorkerId() != null) {
            SysUser worker = users.get(o.getWorkerId());
            if (worker != null) {
                v.setWorkerName(worker.getName());
                v.setWorkerPhone(worker.getPhone());
            }
        }

        List<Attachment> attachments = attachmentMapper.selectList(new LambdaQueryWrapper<Attachment>()
                .eq(Attachment::getOrderId, o.getId()));
        v.setReportImages(attachments.stream()
                .filter(a -> a.getType() == AttachmentType.REPORT).map(Attachment::getUrl).toList());
        v.setFinishImages(attachments.stream()
                .filter(a -> a.getType() == AttachmentType.FINISH).map(Attachment::getUrl).toList());

        v.setEvaluation(evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>()
                .eq(Evaluation::getOrderId, o.getId())));
        return v;
    }
}
