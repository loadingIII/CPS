package com.campus.repair.module.seed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.AuthService;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.dict.Skill;
import com.campus.repair.module.dict.SkillMapper;
import com.campus.repair.module.kb.KbArticle;
import com.campus.repair.module.kb.KbArticleMapper;
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
 * T13 种子数据：首次启动（用户表为空）时初始化基础数据与 12 张演示工单，幂等（重复启动不重复插入）。
 * 账号密码统一 123456。校区为代码枚举（六校区），不落库；详细位置为报修人自由填写的文本。
 * 演示工单覆盖全部六个校区，维修工负责范围主要覆盖广州五校区。
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
    private final KbArticleMapper kbArticleMapper;

    public SeedRunner(SkillMapper skillMapper, UserMapper userMapper,
                      WorkerProfileMapper profileMapper, WorkerSkillMapper skillRelMapper,
                      WorkerScopeMapper scopeMapper, RepairOrderMapper orderMapper,
                      OrderLogMapper logMapper, EvaluationMapper evaluationMapper,
                      WorkerMapper workerMapper, AuthService authService,
                      TransactionTemplate transactionTemplate,
                      KbArticleMapper kbArticleMapper) {
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
        this.kbArticleMapper = kbArticleMapper;
    }

    @Override
    public void run(String... args) {
        seedKbIfEmpty();
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

    /** R2 知识库示例文章：独立幂等，已有数据的环境也能补上 */
    private void seedKbIfEmpty() {
        Long count = kbArticleMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        String[][] articles = {
                {"空调不制冷先查这三处", "空调家电",
                        "1. 确认遥控器电池与模式设置：制冷模式、温度低于室温。\n"
                        + "2. 检查室内机滤网是否积灰，拆下清洗晾干后装回。\n"
                        + "3. 观察外机是否运转；若外机不启动或有异响，请勿自行拆机，提交报修。"},
                {"宿舍跳闸的简单排查", "水电",
                        "1. 先关闭该回路上的大功率电器（电吹风、电热水壶）。\n"
                        + "2. 到配电箱查看是否漏电保护开关跳下，尝试复位一次。\n"
                        + "3. 复位后立即再次跳闸，说明存在漏电或短路，请保持断电并提交报修。"},
                {"多媒体讲台无法开机", "网络多媒体",
                        "1. 确认讲台总电源与中控面板指示灯是否亮起。\n"
                        + "2. 长按中控电源键 5 秒后重新上电，等待 30 秒再开机。\n"
                        + "3. 投影无信号时检查 HDMI 线是否插紧、电脑是否切换了输出。\n"
                        + "4. 以上无效请提交报修，注明教室号与设备编号。"},
                {"门锁卡顿、钥匙转不动", "木工门窗",
                        "1. 不要用力硬拧，避免钥匙断在锁芯内。\n"
                        + "2. 可用铅笔芯粉末少量润滑锁芯后轻轻转动。\n"
                        + "3. 锁舌错位或门体下沉需调整合页，请提交报修由木工处理。"},
                {"电梯困人时的正确做法", "电梯",
                        "1. 保持冷静，按下轿厢内警铃或对讲按钮，等待值班人员回应。\n"
                        + "2. 不要强行扒门、不要攀爬轿顶，避免坠落风险。\n"
                        + "3. 拨打校内应急电话或直接提交紧急报修，说明楼栋、单元与楼层。"},
        };
        LocalDateTime now = LocalDateTime.now();
        for (String[] a : articles) {
            KbArticle article = new KbArticle();
            article.setTitle(a[0]);
            article.setFaultType(a[1]);
            article.setContent(a[2]);
            article.setViewCount(0);
            article.setStatus(true);
            article.setCreatedAt(now);
            article.setUpdatedAt(now);
            kbArticleMapper.insert(article);
        }
        log.info("[Seed] 已初始化 {} 篇知识库示例文章", articles.length);
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
        // 负责校区只按校区枚举配置；大学城校区单派给不负责该校区（龙洞/东风路）的师傅会被资格校验拦下（演示点）
        insertWorker(w01, List.of("水电", "空调家电"), List.of(Campus.UNIVERSITY_TOWN), skillMap);
        insertWorker(w02, List.of("水电"), List.of(Campus.UNIVERSITY_TOWN), skillMap);
        insertWorker(w03, List.of("网络多媒体", "实验设备"),
                List.of(Campus.LONGDONG, Campus.DONGFENG_ROAD), skillMap);
        insertWorker(w04, List.of("木工门窗"), List.of(Campus.UNIVERSITY_TOWN), skillMap);
        insertWorker(w05, List.of("空调家电", "电梯"),
                List.of(Campus.UNIVERSITY_TOWN, Campus.SHAHE), skillMap);
        insertWorker(w06, List.of("水电", "木工门窗"),
                List.of(Campus.LONGDONG, Campus.PANYU), skillMap);
        // 揭阳校区跨市，演示数据中暂不配置驻点维修工，用于展示「候选为空」的派单降级提示

        SysUser s01 = insertUser("student01", "陈明", Role.REPORTER, "13900000201", null, "1号宿舍楼101", hash);
        SysUser s02 = insertUser("student02", "刘婷", Role.REPORTER, "13900000202", null, "2号宿舍楼301", hash);
        SysUser s03 = insertUser("student03", "王磊", Role.REPORTER, "13900000203", null, "2号宿舍楼101", hash);
        SysUser s04 = insertUser("student04", "赵雪", Role.REPORTER, "13900000204", null, "1号宿舍楼301", hash);
        SysUser s05 = insertUser("student05", "钱芳", Role.REPORTER, "13900000205", null, "1号宿舍楼302", hash);
        SysUser t01 = insertUser("teacher01", "孙老师", Role.REPORTER, "13900000206", "计算机学院", null, hash);

        // 报修人所属校区（个人中心资料，报修时作为默认校区）
        assignCampus(s01, Campus.UNIVERSITY_TOWN);
        assignCampus(s02, Campus.UNIVERSITY_TOWN);
        assignCampus(s03, Campus.LONGDONG);
        assignCampus(s04, Campus.UNIVERSITY_TOWN);
        assignCampus(s05, Campus.UNIVERSITY_TOWN);
        assignCampus(t01, Campus.UNIVERSITY_TOWN);

        // ---------------- 演示工单（覆盖全部 7 种状态，log 与状态自洽） ----------------
        LocalDateTime now = LocalDateTime.now();

        // 1 待审核（紧急）
        RepairOrder o1 = newOrder(s01, Campus.UNIVERSITY_TOWN, "1号宿舍楼 101", "空调家电", Urgency.URGENT,
                "宿舍空调不制冷，出风只有常温，晚上热得没法睡", now.minusHours(1));
        // 2 待审核（普通，东风路校区）
        RepairOrder o2 = newOrder(s02, Campus.DONGFENG_ROAD, "一教 201", "网络多媒体",
                Urgency.NORMAL, "一教201多媒体讲台无法开机，投影无信号", now.minusHours(3));
        // 3 待处理（未派单，龙洞校区）
        RepairOrder o3 = newOrder(s03, Campus.LONGDONG, "3号宿舍楼 101", "水电", Urgency.NORMAL,
                "宿舍卫生间水龙头关不严，一直在滴水", now.minusHours(5));
        audit(o3, admin1, now.minusHours(4).minusMinutes(30));
        // 4 待处理（已派单）
        RepairOrder o4 = newOrder(s01, Campus.UNIVERSITY_TOWN, "一教 101", "水电", Urgency.URGENT,
                "一教101讲台插座打火冒烟，有安全隐患，请尽快处理", now.minusHours(2));
        audit(o4, admin1, now.minusHours(1).minusMinutes(48));
        assign(o4, w02, admin1, now.minusMinutes(10));
        // 5 处理中
        RepairOrder o5 = newOrder(s04, Campus.UNIVERSITY_TOWN, "1号宿舍楼 301", "木工门窗", Urgency.NORMAL,
                "宿舍阳台窗户合页断裂，窗扇无法固定，大风天有坠落风险", now.minusHours(26));
        audit(o5, admin1, now.minusHours(25));
        assign(o5, w04, admin1, now.minusHours(24));
        accept(o5, w04, now.minusHours(23));
        // 6 处理中（其他类型，跳过技能匹配）
        RepairOrder o6 = newOrder(s05, Campus.UNIVERSITY_TOWN, "一教 102", "其他", Urgency.NORMAL,
                "一教102门锁卡死，钥匙拧不动，影响上课", now.minusHours(8));
        audit(o6, admin2, now.minusHours(7));
        assign(o6, w01, admin2, now.minusHours(6));
        accept(o6, w01, now.minusHours(5));
        // 7 已完成
        RepairOrder o7 = newOrder(s02, Campus.UNIVERSITY_TOWN, "1号宿舍楼 102", "水电", Urgency.NORMAL,
                "宿舍灯管闪烁，疑似镇流器故障需要更换", now.minusHours(50));
        audit(o7, admin1, now.minusHours(49));
        assign(o7, w01, admin1, now.minusHours(48));
        accept(o7, w01, now.minusHours(47));
        finish(o7, w01, now.minusHours(46), "更换 LED 灯管与镇流器，测试正常发光");
        // 8 已评价
        RepairOrder o8 = newOrder(s03, Campus.UNIVERSITY_TOWN, "二教 101", "空调家电", Urgency.NORMAL,
                "二教101柜机启动后自动跳闸，无法制冷", now.minusHours(80));
        audit(o8, admin1, now.minusHours(79));
        assign(o8, w05, admin1, now.minusHours(78));
        accept(o8, w05, now.minusHours(77));
        finish(o8, w05, now.minusHours(76), "检查为压缩机启动电容损坏，更换电容后运行正常");
        rate(o8, s03, 5, "师傅很专业，处理很快！", now.minusHours(75));
        // 9 已评价（龙洞校区）
        RepairOrder o9 = newOrder(s04, Campus.LONGDONG, "3号宿舍楼 202", "木工门窗", Urgency.NORMAL,
                "宿舍衣柜柜门脱落，铰链螺丝滑丝", now.minusHours(100));
        audit(o9, admin2, now.minusHours(99));
        assign(o9, w06, admin2, now.minusHours(98));
        accept(o9, w06, now.minusHours(97));
        finish(o9, w06, now.minusHours(96), "更换铰链并加固螺丝，柜门开合正常");
        rate(o9, s04, 4, "修好了，态度不错，稍慢了一点", now.minusHours(95));
        // 10 已驳回（沙河校区）
        RepairOrder o10 = newOrder(s05, Campus.SHAHE, "体育馆 北侧卫生间", "水电", Urgency.NORMAL,
                "卫生间顶灯不亮了", now.minusHours(6));
        reject(o10, admin1, "照片无法看清故障位置，请补充清晰图片与具体位置", now.minusHours(5));
        // 11 已取消（番禺校区）
        RepairOrder o11 = newOrder(s01, Campus.PANYU, "图书馆 电梯厅", "电梯", Urgency.NORMAL,
                "图书馆电梯按钮失灵（后发现是自己看错楼层）", now.minusHours(48));
        cancel(o11, s01, now.minusHours(47));
        // 12 待审核（揭阳校区，跨市且暂无驻点维修工，用于演示派单候选为空的降级提示）
        newOrder(s02, Campus.JIEYANG, "教学楼 A 栋 301", "水电", Urgency.NORMAL,
                "教学楼三层走廊照明跳闸，需排查配电箱", now.minusHours(4));

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

    /** 报修人所属校区：只更新 sys_user.campus，维修工服务校区仍由 worker_scope 维护 */
    private void assignCampus(SysUser u, Campus campus) {
        u.setCampus(campus.name());
        userMapper.updateById(u);
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
