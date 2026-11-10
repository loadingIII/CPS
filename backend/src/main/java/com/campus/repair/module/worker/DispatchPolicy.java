package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.dict.Skill;
import com.campus.repair.module.dict.SkillMapper;
import com.campus.repair.module.order.DispatchProperties;
import com.campus.repair.module.order.OrderLogMapper;
import com.campus.repair.module.order.RepairOrder;
import com.campus.repair.module.order.RepairOrderMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 派单策略：硬约束判定 + 推荐评分。
 *
 * <p>所有「某师傅能不能接这张单」「该派给谁」的判断都收敛到这里，
 * 避免派单看板与抢单资格两处规则漂移。
 *
 * <p>硬约束：在岗 + 技能覆盖（「其他」除外）+ 负责校区 + 负载未满 + 不在拒单冷却期。
 * 评分：0.40 技能 + 0.25 负载余量 + 0.20 近30天按时率 + 0.15 评价均分。
 */
@Component
public class DispatchPolicy {

    private final WorkerProfileMapper profileMapper;
    private final WorkerSkillMapper skillRelMapper;
    private final WorkerScopeMapper scopeMapper;
    private final SkillMapper skillMapper;
    private final WorkerMapper workerMapper;
    private final RepairOrderMapper orderMapper;
    private final OrderLogMapper logMapper;
    private final DispatchProperties props;

    public DispatchPolicy(WorkerProfileMapper profileMapper, WorkerSkillMapper skillRelMapper,
                          WorkerScopeMapper scopeMapper, SkillMapper skillMapper,
                          WorkerMapper workerMapper, RepairOrderMapper orderMapper,
                          OrderLogMapper logMapper, DispatchProperties props) {
        this.profileMapper = profileMapper;
        this.skillRelMapper = skillRelMapper;
        this.scopeMapper = scopeMapper;
        this.skillMapper = skillMapper;
        this.workerMapper = workerMapper;
        this.orderMapper = orderMapper;
        this.logMapper = logMapper;
        this.props = props;
    }

    /** 单个维修工的判定上下文 */
    public record Context(Long workerId, String name, boolean hasProfile, boolean onDuty,
                          int loadLimit, BigDecimal score,
                          Map<String, SkillLevel> skills, Set<String> campuses,
                          int load, int done, int onTime) {
    }

    /** 推荐评分结果 */
    public record Score(double total, Map<String, Double> breakdown, List<String> reasons) {
    }

    // ------------------------------------------------------------------
    // 上下文装配
    // ------------------------------------------------------------------

    public Context loadContext(SysUser user) {
        return loadContexts(List.of(user)).get(0);
    }

    public List<Context> loadContexts(List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(SysUser::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, WorkerProfile> profiles = profileMapper.selectList(
                        new LambdaQueryWrapper<WorkerProfile>().in(WorkerProfile::getUserId, ids))
                .stream().collect(Collectors.toMap(WorkerProfile::getUserId, Function.identity()));

        Map<Long, Map<String, SkillLevel>> skillMap = loadSkills(ids);
        Map<Long, Set<String>> scopeMap = loadScopes(ids);
        Map<Long, Integer> loadMap = loadLoads(ids);
        Map<Long, int[]> timeMap = loadTimeliness(ids);

        List<Context> result = new ArrayList<>(users.size());
        for (SysUser u : users) {
            WorkerProfile p = profiles.get(u.getId());
            int[] t = timeMap.getOrDefault(u.getId(), new int[]{0, 0});
            result.add(new Context(
                    u.getId(), u.getName(), p != null,
                    p != null && Boolean.TRUE.equals(p.getOnDuty()),
                    p == null || p.getLoadLimit() == null ? 5 : p.getLoadLimit(),
                    p == null || p.getScore() == null ? BigDecimal.ZERO : p.getScore(),
                    skillMap.getOrDefault(u.getId(), Map.of()),
                    scopeMap.getOrDefault(u.getId(), Set.of()),
                    loadMap.getOrDefault(u.getId(), 0),
                    t[0], t[1]));
        }
        return result;
    }

    private Map<Long, Map<String, SkillLevel>> loadSkills(List<Long> ids) {
        List<WorkerSkill> rels = skillRelMapper.selectList(
                new LambdaQueryWrapper<WorkerSkill>().in(WorkerSkill::getUserId, ids));
        if (rels.isEmpty()) {
            return Map.of();
        }
        Set<Long> skillIds = rels.stream().map(WorkerSkill::getSkillId).collect(Collectors.toSet());
        Map<Long, Skill> dict = skillMapper.selectBatchIds(skillIds).stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        Map<Long, Map<String, SkillLevel>> result = new HashMap<>();
        for (WorkerSkill ws : rels) {
            Skill s = dict.get(ws.getSkillId());
            if (s == null) {
                continue;
            }
            SkillLevel level = ws.getLevel() == null ? SkillLevel.PRIMARY : ws.getLevel();
            result.computeIfAbsent(ws.getUserId(), k -> new HashMap<>())
                    .merge(s.getName(), level,
                            (a, b) -> a == SkillLevel.PRIMARY ? a : b);
        }
        return result;
    }

    private Map<Long, Set<String>> loadScopes(List<Long> ids) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (WorkerScope s : scopeMapper.selectList(
                new LambdaQueryWrapper<WorkerScope>().in(WorkerScope::getUserId, ids))) {
            result.computeIfAbsent(s.getUserId(), k -> new HashSet<>()).add(s.getCampus());
        }
        return result;
    }

    private Map<Long, Integer> loadLoads(List<Long> ids) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : workerMapper.countLoad(ids)) {
            result.put(((Number) row.get("workerId")).longValue(),
                    ((Number) row.get("cnt")).intValue());
        }
        return result;
    }

    private Map<Long, int[]> loadTimeliness(List<Long> ids) {
        Map<Long, int[]> result = new HashMap<>();
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        for (Map<String, Object> row : orderMapper.countTimeliness(ids, from)) {
            Long wid = ((Number) row.get("workerId")).longValue();
            int done = ((Number) row.get("done")).intValue();
            int onTime = row.get("onTime") == null ? 0 : ((Number) row.get("onTime")).intValue();
            result.put(wid, new int[]{done, onTime});
        }
        return result;
    }

    /** 近 N 小时内拒过/被超时回收过该单的维修工 */
    public Set<Long> coolingWorkerIds(Long orderId) {
        if (orderId == null) {
            return Set.of();
        }
        LocalDateTime since = LocalDateTime.now().minusHours(props.getRejectCooldownHours());
        return new HashSet<>(logMapper.selectRecentRejectors(orderId, since));
    }

    // ------------------------------------------------------------------
    // 硬约束
    // ------------------------------------------------------------------

    public List<String> failReasons(Context c, RepairOrder order, Set<Long> cooling, boolean checkLoad) {
        List<String> reasons = new ArrayList<>();
        if (!c.hasProfile()) {
            reasons.add("缺少维修工档案");
        } else if (!c.onDuty()) {
            reasons.add("不在岗");
        }
        if (!"其他".equals(order.getFaultType()) && !c.skills().containsKey(order.getFaultType())) {
            reasons.add("技能不匹配（缺少 " + order.getFaultType() + "）");
        }
        if (!c.campuses().contains(order.getCampus())) {
            reasons.add("负责校区不含工单校区（" + Campus.labelOf(order.getCampus()) + "）");
        }
        if (checkLoad && c.load() >= c.loadLimit()) {
            reasons.add("负载已满（" + c.load() + "/" + c.loadLimit() + "）");
        }
        if (cooling != null && cooling.contains(c.workerId())) {
            reasons.add("近期拒过该单，冷却中");
        }
        return reasons;
    }

    // ------------------------------------------------------------------
    // 推荐评分
    // ------------------------------------------------------------------

    public Score score(Context c, RepairOrder order) {
        DispatchProperties.Weights w = props.getWeights();
        double skill = skillScore(c, order);
        double capacity = capacityScore(c);
        double timeliness = timelinessScore(c);
        double rating = ratingScore(c);

        double total = w.getSkill() * skill
                + w.getCapacity() * capacity
                + w.getTimeliness() * timeliness
                + w.getRating() * rating;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("skill", round(skill));
        breakdown.put("capacity", round(capacity));
        breakdown.put("timeliness", round(timeliness));
        breakdown.put("rating", round(rating));

        return new Score(round(total), breakdown, reasons(c, order, skill, timeliness));
    }

    private double skillScore(Context c, RepairOrder order) {
        if ("其他".equals(order.getFaultType())) {
            return 0.6;
        }
        SkillLevel level = c.skills().get(order.getFaultType());
        if (level == SkillLevel.PRIMARY) {
            return 1.0;
        }
        if (level == SkillLevel.SECONDARY) {
            return 0.7;
        }
        return 0.0;
    }

    private double capacityScore(Context c) {
        if (c.loadLimit() <= 0) {
            return 0.0;
        }
        return clamp(1.0 - (double) c.load() / c.loadLimit());
    }

    /** 贝叶斯平滑：无样本回退 0.8，样本少时向 0.8 收缩，避免一单满分/零分 */
    private double timelinessScore(Context c) {
        return clamp((c.onTime() + 3.2) / (c.done() + 4.0));
    }

    private double ratingScore(Context c) {
        if (c.score() == null) {
            return 0.0;
        }
        return clamp(c.score().doubleValue() / 5.0);
    }

    private List<String> reasons(Context c, RepairOrder order, double skill, double timeliness) {
        List<String> list = new ArrayList<>();
        if ("其他".equals(order.getFaultType())) {
            list.add("其他类型不限技能");
        } else {
            SkillLevel level = c.skills().get(order.getFaultType());
            list.add(order.getFaultType() + (level == SkillLevel.SECONDARY ? "兼修" : "主修"));
        }
        list.add("当前 " + c.load() + "/" + c.loadLimit() + " 单");
        list.add(c.done() == 0
                ? "近30天无完工样本"
                : "近30天按时率 " + Math.round(timeliness * 100) + "%");
        list.add("评分 " + (c.score() == null ? "—" : c.score().toPlainString()));
        return list;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
