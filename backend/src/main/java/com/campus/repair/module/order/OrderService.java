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
import com.campus.repair.module.order.dto.AuditReq;
import com.campus.repair.module.order.dto.AssignReq;
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
import com.campus.repair.module.place.Place;
import com.campus.repair.module.place.PlaceMapper;
import com.campus.repair.module.place.PlaceService;
import com.campus.repair.module.place.PlaceType;
import com.campus.repair.module.place.Skill;
import com.campus.repair.module.place.SkillMapper;
import com.campus.repair.module.worker.WorkerMapper;
import com.campus.repair.module.worker.WorkerService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final PlaceMapper placeMapper;
    private final PlaceService placeService;
    private final SkillMapper skillMapper;
    private final UserMapper userMapper;
    private final WorkerMapper workerMapper;
    private final WorkerService workerService;
    private final IdempotentStore idempotentStore;

    public OrderService(RepairOrderMapper orderMapper, AttachmentMapper attachmentMapper,
                        OrderLogMapper logMapper, EvaluationMapper evaluationMapper,
                        OrderStateMachine stateMachine, OrderLogService logService,
                        PlaceMapper placeMapper, PlaceService placeService,
                        SkillMapper skillMapper, UserMapper userMapper,
                        WorkerMapper workerMapper, WorkerService workerService,
                        IdempotentStore idempotentStore) {
        this.orderMapper = orderMapper;
        this.attachmentMapper = attachmentMapper;
        this.logMapper = logMapper;
        this.evaluationMapper = evaluationMapper;
        this.stateMachine = stateMachine;
        this.logService = logService;
        this.placeMapper = placeMapper;
        this.placeService = placeService;
        this.skillMapper = skillMapper;
        this.userMapper = userMapper;
        this.workerMapper = workerMapper;
        this.workerService = workerService;
        this.idempotentStore = idempotentStore;
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

        Place location = placeMapper.selectById(req.locationId());
        if (location == null || !Boolean.TRUE.equals(location.getEnabled())
                || (location.getType() != PlaceType.SPOT && location.getType() != PlaceType.BUILDING)) {
            throw BusinessException.param("报修位置无效（请选择楼栋或房间点位）");
        }
        Long skillCount = skillMapper.selectCount(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getName, req.faultType())
                .eq(Skill::getEnabled, true));
        if (skillCount == null || skillCount == 0) {
            throw BusinessException.param("故障类型无效: " + req.faultType());
        }

        RepairOrder o = new RepairOrder();
        o.setReporterId(uid);
        o.setLocationId(req.locationId());
        o.setLocationText(placeService.buildPath(req.locationId()));
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

    /** 管理视角工单列表（F3-1 审核队列复用：PENDING_AUDIT/PENDING 固定 紧急度优先+提交时间正序） */
    public PageVO<OrderListVO> pageAdmin(OrderStatus status, Urgency urgency, String faultType,
                                         String keyword, long page, long pageSize) {
        LambdaQueryWrapper<RepairOrder> w = new LambdaQueryWrapper<RepairOrder>()
                .eq(status != null, RepairOrder::getStatus, status)
                .eq(urgency != null, RepairOrder::getUrgency, urgency)
                .eq(StringUtils.hasText(faultType), RepairOrder::getFaultType, faultType);
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
        if (status == OrderStatus.PENDING_AUDIT || status == OrderStatus.PENDING) {
            w.orderByDesc(RepairOrder::getUrgency).orderByAsc(RepairOrder::getSubmittedAt);
        } else {
            w.orderByDesc(RepairOrder::getSubmittedAt);
        }
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), w);
        return new PageVO<>(toListVOs(p.getRecords()), p.getTotal());
    }

    /** 维修工：派给我的工单（F5-1） */
    public PageVO<OrderListVO> mineAssigned(OrderStatus status, long page, long pageSize) {
        OrderStatus st = status == null ? OrderStatus.PENDING : status;
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getWorkerId, AuthContext.userId())
                        .eq(RepairOrder::getStatus, st)
                        .orderByDesc(RepairOrder::getUrgency)
                        .orderByAsc(RepairOrder::getSubmittedAt));
        return new PageVO<>(toListVOs(p.getRecords()), p.getTotal());
    }

    /** 工单详情（本人相关单/管理员） */
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
        String remark = Boolean.TRUE.equals(req.pass()) ? null : req.reason();
        transition(o, Boolean.TRUE.equals(req.pass()) ? OrderAction.AUDIT_PASS : OrderAction.AUDIT_REJECT,
                remark, null);
    }

    @Transactional
    public void cancel(Long orderId) {
        transition(load(orderId), OrderAction.CANCEL, null, null);
    }

    @Transactional
    public void abandon(Long orderId) {
        transition(load(orderId), OrderAction.ABANDON, null, null);
    }

    @Transactional
    public List<com.campus.repair.module.worker.dto.EligibleWorkerVO> eligibleWorkers(Long orderId) {
        return workerService.eligibleWorkersForOrder(load(orderId));
    }

    @Transactional
    public void assign(Long orderId, AssignReq req) {
        RepairOrder o = load(orderId);
        workerService.assertEligible(req.workerId(), o);
        String remark = null;
        if (o.getWorkerId() != null && !o.getWorkerId().equals(req.workerId())) {
            SysUser old = userMapper.selectById(o.getWorkerId());
            remark = "改派自 " + (old == null ? "未知" : old.getName());
        }
        transition(o, OrderAction.ASSIGN, remark, req.workerId());
    }

    @Transactional
    public void accept(Long orderId) {
        transition(load(orderId), OrderAction.ACCEPT, null, null);
    }

    /** 抢单大厅：未被派单的 PENDING 工单中，当前维修工有资格承接的部分（§2.5 硬约束过滤） */
    public PageVO<OrderListVO> grabPool(long page, long pageSize) {
        Page<RepairOrder> p = orderMapper.selectPage(new Page<>(page, Math.min(pageSize, 50)),
                new LambdaQueryWrapper<RepairOrder>()
                        .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                        .isNull(RepairOrder::getWorkerId)
                        .orderByDesc(RepairOrder::getUrgency)
                        .orderByAsc(RepairOrder::getSubmittedAt));
        List<RepairOrder> eligible = p.getRecords().stream()
                .filter(o -> workerService.selfFailReasons(o).isEmpty())
                .toList();
        return new PageVO<>(toListVOs(eligible), eligible.size());
    }

    /** 维修工工单地图：已接单（待处理/处理中）+ 可抢单的坐标点 */
    public List<MapOrderVO> workerMap() {
        Long uid = AuthContext.userId();
        List<RepairOrder> mine = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getWorkerId, uid)
                .in(RepairOrder::getStatus, OrderStatus.PENDING, OrderStatus.PROCESSING)
                .orderByDesc(RepairOrder::getUrgency));
        List<RepairOrder> pool = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                .isNull(RepairOrder::getWorkerId)
                .orderByDesc(RepairOrder::getUrgency)
                .orderByAsc(RepairOrder::getSubmittedAt));
        List<MapOrderVO> result = new ArrayList<>();
        for (RepairOrder o : mine) {
            addMapPoint(result, o, "MINE");
        }
        for (RepairOrder o : pool) {
            if (workerService.selfFailReasons(o).isEmpty()) {
                addMapPoint(result, o, "GRAB");
            }
        }
        return result;
    }

    /** 组装地图点：工单自带选点坐标优先，否则回退位置表 geo；无任何坐标则跳过 */
    private void addMapPoint(List<MapOrderVO> list, RepairOrder o, String kind) {
        Double lng = o.getMapLng();
        Double lat = o.getMapLat();
        if (lng == null || lat == null) {
            Map<String, Object> geo = placeService.findGeoWithFallback(o.getLocationId());
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

    /** 抢单：维修工自助派单给自己（先到先得，乐观锁防并发抢同一单） */
    @Transactional
    public void claim(Long orderId) {
        RepairOrder o = load(orderId);
        Long uid = AuthContext.userId();
        List<String> reasons = workerService.selfFailReasons(o);
        if (!reasons.isEmpty()) {
            throw BusinessException.param("暂不满足承接条件：" + String.join("；", reasons));
        }
        transition(o, OrderAction.CLAIM, "维修工抢单自助接派", uid);
    }

    @Transactional
    public void rejectAssign(Long orderId, ReasonReq req) {
        transition(load(orderId), OrderAction.REJECT_ASSIGN, req.reason(), null);
    }

    @Transactional
    public void finish(Long orderId, FinishReq req) {
        RepairOrder o = load(orderId);
        transition(o, OrderAction.FINISH, null, null);
        saveAttachments(orderId, req.finishImageUrls(), AttachmentType.FINISH);
    }

    @Transactional
    public void transfer(Long orderId, ReasonReq req) {
        transition(load(orderId), OrderAction.TRANSFER, req.reason(), null);
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

    // ==================================================================
    // 内部：乐观锁状态转移 + 时间线（§2.2 实现硬性要求）
    // ==================================================================

    /** 校验 + 条件更新（版本号乐观锁）+ 同事务写 order_log；失败抛 40901 */
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
                .set(RepairOrder::getVersion, newVersion);
        if (o.getAuditedAt() != null) {
            w.set(RepairOrder::getAuditedAt, o.getAuditedAt());
        }
        if (o.getAssignedAt() != null) {
            w.set(RepairOrder::getAssignedAt, o.getAssignedAt());
        }
        if (o.getAcceptedAt() != null) {
            w.set(RepairOrder::getAcceptedAt, o.getAcceptedAt());
        }
        if (o.getCompletedAt() != null) {
            w.set(RepairOrder::getCompletedAt, o.getCompletedAt());
        }
        if (o.getRatedAt() != null) {
            w.set(RepairOrder::getRatedAt, o.getRatedAt());
        }
        switch (action) {
            case ASSIGN -> w.set(RepairOrder::getWorkerId, extraWorkerId);
            // 抢单并发：必须仍为未派单（先到先得，§2.4 同源乐观锁）
            case CLAIM -> {
                w.isNull(RepairOrder::getWorkerId);
                w.set(RepairOrder::getWorkerId, extraWorkerId);
            }
            case REJECT_ASSIGN, TRANSFER -> w.set(RepairOrder::getWorkerId, null);
            // 并发接单幂等：必须仍是被派给本人且处于 PENDING（§2.4）
            case ACCEPT -> w.eq(RepairOrder::getWorkerId, op.userId());
            case RESUBMIT -> w.set(RepairOrder::getDescription, o.getDescription());
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

    /** 详情/时间线读权限：报修人本人、被派维修工、管理员 */
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
                if (o.getWorkerId() == null || !u.userId().equals(o.getWorkerId())) {
                    throw BusinessException.notFound("工单不存在");
                }
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
        OrderDetailVO v = new OrderDetailVO();
        v.setId(o.getId());
        v.setOrderNo(o.getOrderNo());
        v.setReporterId(o.getReporterId());
        v.setWorkerId(o.getWorkerId());
        v.setLocationId(o.getLocationId());
        v.setLocationText(o.getLocationText());
        // 导航坐标：报修时地图选点优先，否则沿位置父链回退取最近一级 geo
        if (o.getMapLng() != null && o.getMapLat() != null) {
            v.setLocationLng(o.getMapLng());
            v.setLocationLat(o.getMapLat());
            v.setMapName(o.getMapName());
        } else {
            Map<String, Object> geo = placeService.findGeoWithFallback(o.getLocationId());
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
            v.setReporterPhone(reporter.getPhone());
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
