package com.campus.repair.module.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.AuthService;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.dict.Skill;
import com.campus.repair.module.dict.SkillMapper;
import com.campus.repair.module.order.Evaluation;
import com.campus.repair.module.order.EvaluationMapper;
import com.campus.repair.module.order.OrderLog;
import com.campus.repair.module.order.OrderLogMapper;
import com.campus.repair.module.order.RepairOrder;
import com.campus.repair.module.order.RepairOrderMapper;
import com.campus.repair.module.order.Urgency;
import com.campus.repair.module.order.statemachine.OrderAction;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.worker.WorkerMapper;
import com.campus.repair.module.worker.WorkerProfile;
import com.campus.repair.module.worker.WorkerProfileMapper;
import com.campus.repair.module.worker.WorkerScope;
import com.campus.repair.module.worker.WorkerScopeMapper;
import com.campus.repair.module.worker.WorkerSkill;
import com.campus.repair.module.worker.WorkerSkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * T13 种子数据：首次启动（用户表为空）时初始化基础数据与 11 张演示工单，幂等（重复启动不重复插入）。
 * 账号密码统一 123456。校区为代码枚举，不落库；详细位置为报修人自由填写的文本。
 */
@Component
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final SkillMapper skillMapper;
    private final UserMapper userMapper;
    private final WorkerProfileMapper profileMapper;
    private final WorkerSkillMapper skillRelMapper;
    private final WorkerScopeMapper scopeMapper;
    private final RepairOrderMapper orderMapper;
    private final OrderLogMapper logMapper;
    private final EvaluationMapper evaluationMapper;
    private final WorkerMapper workerMapper;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public SeedRunner(SkillMapper skillMapper, UserMapper userMapper,
                      WorkerProfileMapper profileMapper, WorkerSkillMapper skillRelMapper,
                      WorkerScopeMapper scopeMapper, RepairOrderMapper orderMapper,
                      OrderLogMapper logMapper, EvaluationMapper evaluationMapper,
                      WorkerMapper workerMapper, AuthService authService,
                      TransactionTemplate transactionTemplate) {
        this.skillMapper = skillMapper;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.skillRelMapper = skillRelMapper;
        this.scopeMapper = scopeMapper;
        this.orderMapper = orderMapper;
        this.logMapper = logMapper;
        this.evaluationMapper = evaluationMapper;
        this.workerMapper = workerMapper;
        this.authService = authService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("[Seed] 已存在用户数据，跳过种子初始化");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            seed();
            log.info("[Seed] 种子数据初始化完成：账号密码统一 123456");
        });
    }

    private void seed() {
        String hash = authService.encoder().encode("123456");

        // ---------------- 技能 / 故障类型 ----------------
        var skillMap = new java.util.HashMap<String, Long>();
        for (String name : new String[]{"水电", "木工门窗", "网络多媒体", "空调家电", "电梯", "实验设备", "其他"}) {
            Skill s = new Skill();
            s.setName(name);
            s.setEnabled(true);
            skillMapper.insert(s);
            skillMap.put(name, s.getId());
        }

        // ---------------- 账号 ----------------
        SysUser sa = insertUser("sa", "系统管理员", Role.SYS_ADMIN, "13900000001", null, null, hash);
        SysUser admin1 = insertUser("admin1", "李老师", Role.ADMIN, "13900000002", "后勤处", null, hash);
        SysUser admin2 = insertUser("admin2", "王科长", Role.ADMIN, "13900000003", "后勤处", null, hash);

        SysUser w01 = insertUser("worker01", "张强", Role.REPAIRER, "13900000101", null, null, hash);
        SysUser w02 = insertUser("worker02", "李国栋", Role.REPAIRER, "13900000102", null, null, hash);
        SysUser w03 = insertUser("worker03", "赵敏", Role.REPAIRER, "13900000103", null, null, hash);
        SysUser w04 = insertUser("worker04", "孙志刚", Role.REPAIRER, "13900000104", null, null, hash);
        SysUser w05 = insertUser("worker05", "周建国", Role.REPAIRER, "13900000105", null, null, hash);
        SysUser w06 = insertUser("worker06", "吴秀兰", Role.REPAIRER, "13900000106", null, null, hash);
        // 负责校区只按校区枚举配置；东校区单派给西校区师傅会被资格校验拦下（演示点）
        insertWorker(w01, List.of("水电", "空调家电"), List.of(Campus.EAST), skillMap);
        insertWorker(w02, List.of("水电"), List.of(Campus.EAST), skillMap);
        insertWorker(w03, List.of("网络多媒体", "实验设备"), List.of(Campus.WEST), skillMap);
        insertWorker(w04, List.of("木工门窗"), List.of(Campus.EAST), skillMap);
        insertWorker(w05, List.of("空调家电", "电梯"), List.of(Campus.EAST), skillMap);
        insertWorker(w06, List.of("水电", "木工门窗"), List.of(Campus.WEST, Campus.SOUTH), skillMap);

        SysUser s01 = insertUser("student01", "陈明", Role.REPORTER, "13900000201", null, "1号宿舍楼101", hash);
        SysUser s02 = insertUser("student02", "刘婷", Role.REPORTER, "13900000202", null, "2号宿舍楼301", hash);
        SysUser s03 = insertUser("student03", "王磊", Role.REPORTER, "13900000203", null, "2号宿舍楼101", hash);
        SysUser s04 = insertUser("student04", "赵雪", Role.REPORTER, "13900000204", null, "1号宿舍楼301", hash);
        SysUser s05 = insertUser("student05", "钱芳", Role.REPORTER, "13900000205", null, "1号宿舍楼302", hash);
        insertUser("teacher01", "孙老师", Role.REPORTER, "13900000206", "计算机学院", null, hash);

        // ---------------- 演示工单（覆盖全部 7 种状态，log 与状态自洽） ----------------
        LocalDateTime now = LocalDateTime.now();

        // 1 待审核（紧急）
        RepairOrder o1 = newOrder(s01, Campus.EAST, "1号宿舍楼 101", "空调家电", Urgency.URGENT,
                "宿舍空调不制冷，出风只有常温，晚上热得没法睡", now.minusHours(1));
        // 2 待审核（普通）
        RepairOrder o2 = newOrder(s02, Campus.EAST, "一教 201", "网络多媒体",
                Urgency.NORMAL, "一教201多媒体讲台无法开机，投影无信号", now.minusHours(3));
        // 3 待处理（未派单，西校区）
        RepairOrder o3 = newOrder(s03, Campus.WEST, "3号宿舍楼 101", "水电", Urgency.NORMAL,
                "宿舍卫生间水龙头关不严，一直在滴水", now.minusHours(5));
        audit(o3, admin1, now.minusHours(4).minusMinutes(30));
        // 4 待处理（已派单）
        RepairOrder o4 = newOrder(s01, Campus.EAST, "一教 101", "水电", Urgency.URGENT,
                "一教101讲台插座打火冒烟，有安全隐患，请尽快处理", now.minusHours(2));
        audit(o4, admin1, now.minusHours(1).minusMinutes(48));
        assign(o4, w02, admin1, now.minusMinutes(10));
        // 5 处理中
        RepairOrder o5 = newOrder(s04, Campus.EAST, "1号宿舍楼 301", "木工门窗", Urgency.NORMAL,
                "宿舍阳台窗户合页断裂，窗扇无法固定，大风天有坠落风险", now.minusHours(26));
        audit(o5, admin1, now.minusHours(25));
        assign(o5, w04, admin1, now.minusHours(24));
        accept(o5, w04, now.minusHours(23));
        // 6 处理中（其他类型，跳过技能匹配）
        RepairOrder o6 = newOrder(s05, Campus.EAST, "一教 102", "其他", Urgency.NORMAL,
                "一教102门锁卡死，钥匙拧不动，影响上课", now.minusHours(8));
        audit(o6, admin2, now.minusHours(7));
        assign(o6, w01, admin2, now.minusHours(6));
        accept(o6, w01, now.minusHours(5));
        // 7 已完成
        RepairOrder o7 = newOrder(s02, Campus.EAST, "1号宿舍楼 102", "水电", Urgency.NORMAL,
                "宿舍灯管闪烁，疑似镇流器故障需要更换", now.minusHours(50));
        audit(o7, admin1, now.minusHours(49));
        assign(o7, w01, admin1, now.minusHours(48));
        accept(o7, w01, now.minusHours(47));
        finish(o7, w01, now.minusHours(46), "更换 LED 灯管与镇流器，测试正常发光");
        // 8 已评价
        RepairOrder o8 = newOrder(s03, Campus.EAST, "二教 101", "空调家电", Urgency.NORMAL,
                "二教101柜机启动后自动跳闸，无法制冷", now.minusHours(80));
        audit(o8, admin1, now.minusHours(79));
        assign(o8, w05, admin1, now.minusHours(78));
        accept(o8, w05, now.minusHours(77));
        finish(o8, w05, now.minusHours(76), "检查为压缩机启动电容损坏，更换电容后运行正常");
        rate(o8, s03, 5, "师傅很专业，处理很快！", now.minusHours(75));
        // 9 已评价（西校区）
        RepairOrder o9 = newOrder(s04, Campus.WEST, "3号宿舍楼 202", "木工门窗", Urgency.NORMAL,
                "宿舍衣柜柜门脱落，铰链螺丝滑丝", now.minusHours(100));
        audit(o9, admin2, now.minusHours(99));
        assign(o9, w06, admin2, now.minusHours(98));
        accept(o9, w06, now.minusHours(97));
        finish(o9, w06, now.minusHours(96), "更换铰链并加固螺丝，柜门开合正常");
        rate(o9, s04, 4, "修好了，态度不错，稍慢了一点", now.minusHours(95));
        // 10 已驳回
        RepairOrder o10 = newOrder(s05, Campus.WEST, "体育馆 北侧卫生间", "水电", Urgency.NORMAL,
                "卫生间顶灯不亮了", now.minusHours(6));
        reject(o10, admin1, "照片无法看清故障位置，请补充清晰图片与具体位置", now.minusHours(5));
        // 11 已取消（南校区）
        RepairOrder o11 = newOrder(s01, Campus.SOUTH, "图书馆 电梯厅", "电梯", Urgency.NORMAL,
                "图书馆电梯按钮失灵（后发现是自己看错楼层）", now.minusHours(48));
        cancel(o11, s01, now.minusHours(47));

        // ---------------- 评分重算 ----------------
        workerMapper.recalcScore(w05.getId());
        workerMapper.recalcScore(w06.getId());
    }

    // ------------------------------------------------------------------
    // 组装辅助
    // ------------------------------------------------------------------

    private SysUser insertUser(String jobNo, String name, Role role, String phone,
                               String department, String address, String hash) {
        SysUser u = new SysUser();
        u.setJobNo(jobNo);
        u.setName(name);
        u.setRole(role);
        u.setPhone(phone);
        u.setDepartment(department);
        u.setAddress(address);
        u.setStatus(1);
        u.setPasswordHash(hash);
        userMapper.insert(u);
        return u;
    }

    private void insertWorker(SysUser u, List<String> skillNames, List<Campus> campuses,
                              Map<String, Long> skillMap) {
        WorkerProfile p = new WorkerProfile();
        p.setUserId(u.getId());
        p.setOnDuty(true);
        p.setLoadLimit(5);
        p.setScore(new BigDecimal("5.00"));
        profileMapper.insert(p);
        // 演示数据：第一项为主修，其余为兼修（派单评分 SkillMatch 因子）
        for (int i = 0; i < skillNames.size(); i++) {
            WorkerSkill ws = new WorkerSkill();
            ws.setUserId(u.getId());
            ws.setSkillId(skillMap.get(skillNames.get(i)));
            ws.setLevel(i == 0 ? com.campus.repair.module.worker.SkillLevel.PRIMARY
                    : com.campus.repair.module.worker.SkillLevel.SECONDARY);
            skillRelMapper.insert(ws);
        }
        for (Campus c : campuses) {
            WorkerScope s = new WorkerScope();
            s.setUserId(u.getId());
            s.setCampus(c.name());
            scopeMapper.insert(s);
        }
    }

    /** 新建工单（工单号按提交日期生成，含唯一键重试） */
    private RepairOrder newOrder(SysUser reporter, Campus campus, String locationDetail,
                                 String faultType, Urgency urgency, String desc, LocalDateTime submittedAt) {
        RepairOrder o = new RepairOrder();
        o.setReporterId(reporter.getId());
        o.setCampus(campus.name());
        o.setLocationText(Campus.format(campus.name(), locationDetail));
        o.setFaultType(faultType);
        o.setUrgency(urgency);
        o.setDescription(desc);
        o.setStatus(OrderStatus.PENDING_AUDIT);
        o.setIsOverdue(false);
        o.setVersion(0);
        o.setDispatchCount(0);
        o.setRejectCount(0);
        o.setSubmittedAt(submittedAt);
        String prefix = "BX" + submittedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int attempt = 1; attempt <= 3; attempt++) {
            int seq = orderMapper.selectMaxSeqToday(prefix) + 1;
            o.setOrderNo(prefix + String.format("%04d", seq));
            try {
                orderMapper.insert(o);
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == 3) {
                    throw new IllegalStateException("种子工单号生成冲突");
                }
            }
        }
        addLog(o.getId(), OrderAction.SUBMIT, reporter.getId(), Role.REPORTER, null, submittedAt);
        return o;
    }

    private void audit(RepairOrder o, SysUser admin, LocalDateTime t) {
        o.setStatus(OrderStatus.PENDING);
        o.setAuditedAt(t);
        o.setGrabOpenAt(t.plusMinutes(o.getUrgency() == Urgency.URGENT ? 0 : 20));
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.AUDIT_PASS, admin.getId(), admin.getRole(), null, t);
    }

    private void reject(RepairOrder o, SysUser admin, String reason, LocalDateTime t) {
        o.setStatus(OrderStatus.REJECTED);
        o.setAuditedAt(t);
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.AUDIT_REJECT, admin.getId(), admin.getRole(), reason, t);
    }

    private void assign(RepairOrder o, SysUser worker, SysUser admin, LocalDateTime t) {
        o.setWorkerId(worker.getId());
        o.setAssignedAt(t);
        o.setDispatchSource(com.campus.repair.module.order.DispatchSource.MANUAL);
        o.setDispatchCount((o.getDispatchCount() == null ? 0 : o.getDispatchCount()) + 1);
        o.setAssignExpiresAt(t.plusMinutes(o.getUrgency() == Urgency.URGENT ? 30 : 120));
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.ASSIGN, admin.getId(), admin.getRole(),
                "派单给 " + worker.getName(), t);
    }

    private void accept(RepairOrder o, SysUser worker, LocalDateTime t) {
        o.setStatus(OrderStatus.PROCESSING);
        o.setAcceptedAt(t);
        o.setAssignExpiresAt(null);
        o.setDueAt(t.plusHours(o.getUrgency() == Urgency.URGENT ? 4 : 24));
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.ACCEPT, worker.getId(), worker.getRole(), null, t);
    }

    private void finish(RepairOrder o, SysUser worker, LocalDateTime t, String summary) {
        o.setStatus(OrderStatus.COMPLETED);
        o.setCompletedAt(t);
        o.setFinishSummary(summary);
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.FINISH, worker.getId(), worker.getRole(), summary, t);
    }

    private void rate(RepairOrder o, SysUser reporter, int rating, String content, LocalDateTime t) {
        o.setStatus(OrderStatus.EVALUATED);
        o.setRatedAt(t);
        orderMapper.updateById(o);
        Evaluation e = new Evaluation();
        e.setOrderId(o.getId());
        e.setRating(rating);
        e.setContent(content);
        e.setIsDefault(false);
        e.setCreatedAt(t);
        evaluationMapper.insert(e);
        addLog(o.getId(), OrderAction.RATE, reporter.getId(), reporter.getRole(),
                rating + " 星评价", t);
    }

    private void cancel(RepairOrder o, SysUser reporter, LocalDateTime t) {
        o.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(o);
        addLog(o.getId(), OrderAction.CANCEL, reporter.getId(), reporter.getRole(), "报修人撤单", t);
    }

    private void addLog(Long orderId, OrderAction action, Long operatorId, Role role,
                        String remark, LocalDateTime t) {
        OrderLog l = new OrderLog();
        l.setOrderId(orderId);
        l.setAction(action.label());
        l.setOperatorId(operatorId);
        l.setOperatorRole(role == null ? "SYSTEM" : role.name());
        l.setRemark(remark);
        l.setCreatedAt(t);
        logMapper.insert(l);
    }
}
