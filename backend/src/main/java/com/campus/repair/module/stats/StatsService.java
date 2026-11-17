package com.campus.repair.module.stats;

import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.order.RepairOrderMapper;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.stats.dto.AiSuggestionStatVO;
import com.campus.repair.module.stats.dto.AnomalyOrderVO;
import com.campus.repair.module.stats.dto.CampusStatVO;
import com.campus.repair.module.stats.dto.OntimePointVO;
import com.campus.repair.module.stats.dto.WorkerPerfVO;
import com.campus.repair.module.stats.dto.WorkerProfileVO;
import com.campus.repair.module.worker.WorkerService;
import com.campus.repair.module.worker.WorkerVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R2 统计口径（与《第二阶段计划书》3.6 一致）：
 * 按时完成率 = due_at 前完工数 / 窗口内已完工数；分母按 completed_at 落在窗口内。
 *
 * <p>R3 增加：校区维度、异常工单、维修工画像、AI 采纳率。所有比例在服务层换算，
 * 分母为 0 时返回 null，前端显示“—”。</p>
 */
@Service
public class StatsService {

    private static final int MIN_SAMPLE = 5;

    /** 异常看板展示顺序 */
    private static final List<String> TYPE_ORDER = List.of(
            "LONG_PENDING_AUDIT", "LONG_POOL", "OFFER_EXPIRED",
            "PROCESS_OVERDUE", "MULTI_DISPATCH", "MULTI_TIMEOUT", "REPAIR");

    private final RepairOrderMapper orderMapper;
    private final StatsMapper statsMapper;
    private final WorkerService workerService;
    private final UserMapper userMapper;

    public StatsService(RepairOrderMapper orderMapper,
                        StatsMapper statsMapper,
                        WorkerService workerService,
                        UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.statsMapper = statsMapper;
        this.workerService = workerService;
        this.userMapper = userMapper;
    }

    /* ==================== R2：按时率与绩效 ==================== */

    public List<OntimePointVO> ontime(int days) {
        int window = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<String, Long> submitted = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countBySubmittedDay(from, to)) {
            submitted.put(String.valueOf(row.get("day")), number(row.get("total")));
        }
        Map<String, long[]> completed = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countOntimeByDay(from, to)) {
            completed.put(String.valueOf(row.get("day")),
                    new long[]{number(row.get("done")), number(row.get("onTime"))});
        }

        List<OntimePointVO> result = new ArrayList<>(window);
        for (int offset = window - 1; offset >= 0; offset--) {
            String day = today.minusDays(offset).toString();
            long[] c = completed.getOrDefault(day, new long[]{0, 0});
            OntimePointVO vo = new OntimePointVO();
            vo.setDay(day);
            vo.setSubmitted(submitted.getOrDefault(day, 0L));
            vo.setCompleted(c[0]);
            vo.setOnTime(c[1]);
            vo.setOnTimeRate(c[0] == 0 ? null
                    : BigDecimal.valueOf(c[1]).divide(BigDecimal.valueOf(c[0]), 4, RoundingMode.HALF_UP)
                            .doubleValue());
            result.add(vo);
        }
        return result;
    }

    public List<WorkerPerfVO> workerPerformance(int days) {
        int window = Math.max(1, Math.min(days, 180));
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        List<WorkerPerfVO> result = new ArrayList<>();
        for (Map<String, Object> row : orderMapper.selectWorkerPerformance(from)) {
            WorkerPerfVO vo = new WorkerPerfVO();
            vo.setWorkerId(((Number) row.get("workerId")).longValue());
            vo.setName(row.get("name") == null ? "未知" : String.valueOf(row.get("name")));
            vo.setDone(number(row.get("done")));
            vo.setOnTime(number(row.get("onTime")));
            vo.setOnTimeRate(vo.getDone() == 0 ? null
                    : BigDecimal.valueOf(vo.getOnTime())
                            .divide(BigDecimal.valueOf(vo.getDone()), 4, RoundingMode.HALF_UP).doubleValue());
            Object avg = row.get("avgRating");
            vo.setAvgRating(avg == null ? null : ((Number) avg).doubleValue());
            Object score = row.get("score");
            vo.setScore(score == null ? null : new BigDecimal(String.valueOf(score)));
            vo.setSampleEnough(vo.getDone() >= MIN_SAMPLE);
            result.add(vo);
        }
        // 样本充足的优先排名，其次按完成量
        result.sort((a, b) -> {
            if (a.isSampleEnough() != b.isSampleEnough()) {
                return a.isSampleEnough() ? -1 : 1;
            }
            return Long.compare(b.getDone(), a.getDone());
        });
        return result;
    }

    /* ==================== R3：校区维度 ==================== */

    public List<CampusStatVO> campus(int days) {
        int window = clampDays(days, 180);
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        Map<String, long[]> submitted = new HashMap<>();
        for (Map<String, Object> row : statsMapper.campusSubmitted(from, to)) {
            submitted.put(String.valueOf(row.get("campus")),
                    new long[]{number(row.get("total")), number(row.get("urgent"))});
        }

        Map<String, double[]> completed = new HashMap<>();
        for (Map<String, Object> row : statsMapper.campusCompleted(from, to)) {
            completed.put(String.valueOf(row.get("campus")),
                    new double[]{number(row.get("completed")), number(row.get("onTime")),
                            decimal(row.get("avgMinutes"))});
        }

        Map<String, List<String>> topTypes = new LinkedHashMap<>();
        for (Map<String, Object> row : statsMapper.campusFaultTypes(from, to)) {
            String campus = String.valueOf(row.get("campus"));
            List<String> list = topTypes.computeIfAbsent(campus, k -> new ArrayList<>());
            if (list.size() < 3) {
                list.add(String.valueOf(row.get("faultType")));
            }
        }

        List<CampusStatVO> result = new ArrayList<>();
        for (Campus c : Campus.values()) {
            long[] s = submitted.getOrDefault(c.name(), new long[]{0, 0});
            double[] d = completed.getOrDefault(c.name(), new double[]{0, 0, 0});
            long total = s[0];
            long done = (long) d[0];
            Double onTimeRate = done == 0 ? null : round4(d[1] / done);
            Double avgHours = d[2] <= 0 ? null : round2(d[2] / 60.0);
            Double urgentRate = total == 0 ? null : round4(s[1] * 1.0 / total);
            result.add(new CampusStatVO(c.name(), c.label(), total, done,
                    onTimeRate, avgHours, urgentRate, topTypes.getOrDefault(c.name(), List.of())));
        }
        return result;
    }

    /* ==================== R3：异常看板 ==================== */

    public List<AnomalyOrderVO> anomalies(int days) {
        int window = clampDays(days, 180);
        LocalDateTime now = LocalDateTime.now();
        List<Raw> raws = new ArrayList<>();

        addRaws(raws, "LONG_PENDING_AUDIT", "待审核超 24h",
                statsMapper.longPendingAudit(now.minusHours(24)));
        addRaws(raws, "LONG_POOL", "待派单超 12h",
                statsMapper.longPendingPool(now.minusHours(12)));
        addRaws(raws, "OFFER_EXPIRED", "已派待接超时",
                statsMapper.offerExpired(now));
        addRaws(raws, "PROCESS_OVERDUE", "处理超时未回收",
                statsMapper.processOverdue(now));
        addRaws(raws, "MULTI_DISPATCH", "多次派单",
                statsMapper.multiDispatch());
        addRaws(raws, "MULTI_TIMEOUT", "多次超时",
                statsMapper.multiTimeout());

        for (RepairPair pair : repairPairs(now.minusDays(window))) {
            raws.add(new Raw("REPAIR", "7 天内返修", pair.next().row(),
                    pair.next().submittedAt(), pair.original().orderNo()));
        }

        Set<Long> workerIds = raws.stream()
                .map(r -> longOrNull(r.row().get("workerId")))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> names = workerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(workerIds).stream()
                        .collect(Collectors.toMap(u -> u.getId(), u -> u.getName()));

        return raws.stream()
                .map(r -> {
                    Map<String, Object> row = r.row();
                    Long workerId = longOrNull(row.get("workerId"));
                    LocalDateTime base = r.baseTime();
                    long age = base == null ? 0
                            : Math.max(0, Duration.between(base, now).toHours());
                    return new AnomalyOrderVO(
                            r.type(), r.label(),
                            longOrNull(row.get("orderId")),
                            string(row.get("orderNo")),
                            Campus.labelOf(string(row.get("campus"))),
                            string(row.get("locationText")),
                            statusLabel(row.get("status")),
                            workerId == null ? null : names.get(workerId),
                            age,
                            intOrNull(row.get("dispatchCount")),
                            intOrNull(row.get("rejectCount")),
                            toDateTime(row.get("submittedAt")),
                            toDateTime(row.get("dueAt")),
                            r.relatedOrderNo());
                })
                .sorted(Comparator.comparingInt((AnomalyOrderVO v) -> {
                            int idx = TYPE_ORDER.indexOf(v.type());
                            return idx < 0 ? TYPE_ORDER.size() : idx;
                        })
                        .thenComparing(Comparator.comparingLong(AnomalyOrderVO::ageHours).reversed()))
                .toList();
    }

    private void addRaws(List<Raw> raws, String type, String label, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            raws.add(new Raw(type, label, row, toDateTime(row.get("baseTime")), null));
        }
    }

    /* ==================== R3：维修工画像 ==================== */

    public WorkerProfileVO workerProfile(Long workerId, int days) {
        WorkerVO base = workerService.detail(workerId);
        int window = clampDays(days, 365);
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        Map<String, Object> stat = statsMapper.workerStats(workerId, from, to);
        long done = stat == null ? 0 : number(stat.get("done"));
        long onTime = stat == null ? 0 : number(stat.get("onTime"));
        Double avgRating = stat == null ? null : decimalOrNull(stat.get("avgRating"));
        Double avgHours = stat == null ? null : hoursOrNull(stat.get("avgMinutes"));

        long repairCount = repairPairs(from).stream()
                .filter(p -> workerId.equals(p.original().workerId()))
                .filter(p -> p.original().completedAt() != null
                        && !p.original().completedAt().isBefore(from))
                .count();
        long recycles = statsMapper.countOverdueRecycles(workerId, from);

        List<WorkerProfileVO.RecentOrder> recent = statsMapper.recentCompletedByWorker(workerId)
                .stream()
                .map(row -> new WorkerProfileVO.RecentOrder(
                        string(row.get("orderNo")),
                        string(row.get("faultType")),
                        string(row.get("locationText")),
                        toDateTime(row.get("completedAt")),
                        Boolean.TRUE.equals(row.get("isOverdue")),
                        intOrNull(row.get("rating"))))
                .toList();

        return new WorkerProfileVO(
                base.getId(), base.getName(), base.getJobNo(), base.getPhone(),
                base.getOnDuty(), base.getLoadLimit(), base.getCurrentLoad(), base.getScore(),
                base.getSkills(), base.getCampuses(),
                done, onTime,
                done == 0 ? null : round4(onTime * 1.0 / done),
                avgRating == null ? null : round2(avgRating),
                done == 0 ? null : round4(repairCount * 1.0 / done),
                avgHours,
                recycles,
                done >= MIN_SAMPLE,
                recent);
    }

    /* ==================== R3：AI 采纳率 ==================== */

    public AiSuggestionStatVO aiSuggestions(int days) {
        int window = clampDays(days, 180);
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();

        Map<String, Object> summary = statsMapper.aiSuggestionSummary(from, to);
        long suggested = summary == null ? 0 : number(summary.get("suggested"));
        long used = summary == null ? 0 : number(summary.get("used"));
        Double rate = suggested == 0 ? null : round4(used * 1.0 / suggested);
        List<AiSuggestionStatVO.TypeStat> byType = statsMapper.aiSuggestionByType(from, to).stream()
                .map(row -> new AiSuggestionStatVO.TypeStat(
                        string(row.get("type")), number(row.get("suggested")), number(row.get("used"))))
                .toList();
        return new AiSuggestionStatVO(suggested, used, rate, byType);
    }

    /* ==================== 返修识别 ==================== */

    /**
     * 返修：同校区 + 同故障类型 + 位置文本归一化后相同，上一单完工后 7 天内再次提交。
     * 只认第一次后续单，排除已取消单。
     */
    private List<RepairPair> repairPairs(LocalDateTime from) {
        Map<String, List<RepairRow>> groups = new LinkedHashMap<>();
        // 多取 7 天，避免漏掉“窗口前完工、窗口内再次报修”的返修
        for (Map<String, Object> row : statsMapper.ordersForRepair(from.minusDays(7))) {
            RepairRow r = new RepairRow(
                    longOrNull(row.get("orderId")),
                    string(row.get("orderNo")),
                    string(row.get("campus")),
                    string(row.get("faultType")),
                    string(row.get("locationText")),
                    string(row.get("status")),
                    longOrNull(row.get("workerId")),
                    toDateTime(row.get("submittedAt")),
                    toDateTime(row.get("completedAt")));
            String key = r.campus() + "|" + r.faultType() + "|" + normalizeLocation(r.locationText());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<RepairPair> pairs = new ArrayList<>();
        for (List<RepairRow> list : groups.values()) {
            list.sort(Comparator.comparing(RepairRow::submittedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < list.size(); i++) {
                RepairRow original = list.get(i);
                if (original.completedAt() == null
                        || (!"COMPLETED".equals(original.status()) && !"EVALUATED".equals(original.status()))) {
                    continue;
                }
                for (int j = i + 1; j < list.size(); j++) {
                    RepairRow next = list.get(j);
                    if (next.submittedAt() == null) {
                        continue;
                    }
                    if (!next.submittedAt().isAfter(original.completedAt())) {
                        continue;
                    }
                    if (next.submittedAt().isAfter(original.completedAt().plusDays(7))) {
                        break;
                    }
                    pairs.add(new RepairPair(original, next));
                    break;
                }
            }
        }
        return pairs;
    }

    private String normalizeLocation(String text) {
        String s = text == null ? "" : text;
        int dot = s.indexOf('·');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        s = s.replaceAll("\\s+", "")
                .replace("栋", "号楼")
                .replaceAll("[，,。.;；、]", "");
        return s.isEmpty() ? "unknown" : s;
    }

    /* ==================== 工具 ==================== */

    private int clampDays(int days, int max) {
        return Math.max(1, Math.min(days, max));
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private Long longOrNull(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private double decimal(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    private Double decimalOrNull(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private Double hoursOrNull(Object minutes) {
        return minutes instanceof Number n ? round2(n.doubleValue() / 60.0) : null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String statusLabel(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(String.valueOf(raw)).label();
        } catch (Exception e) {
            return String.valueOf(raw);
        }
    }

    private LocalDateTime toDateTime(Object raw) {
        if (raw instanceof LocalDateTime dt) {
            return dt;
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    private Double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private Double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private record Raw(String type, String label, Map<String, Object> row,
                       LocalDateTime baseTime, String relatedOrderNo) {
    }

    private record RepairRow(Long orderId, String orderNo, String campus, String faultType,
                             String locationText, String status, Long workerId,
                             LocalDateTime submittedAt, LocalDateTime completedAt) {
        Map<String, Object> row() {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", orderId);
            m.put("orderNo", orderNo);
            m.put("campus", campus);
            m.put("locationText", locationText);
            m.put("status", status);
            m.put("workerId", workerId);
            m.put("submittedAt", submittedAt);
            m.put("completedAt", completedAt);
            return m;
        }
    }

    private record RepairPair(RepairRow original, RepairRow next) {
    }
}
