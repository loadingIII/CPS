package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.AuthService;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.dict.Skill;
import com.campus.repair.module.dict.SkillMapper;
import com.campus.repair.module.order.OrderListVO;
import com.campus.repair.module.order.RepairOrder;
import com.campus.repair.module.worker.dto.EligibleWorkerVO;
import com.campus.repair.module.worker.dto.WorkerCreateReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkerService {

    private final WorkerMapper workerMapper;
    private final UserMapper userMapper;
    private final WorkerProfileMapper profileMapper;
    private final WorkerSkillMapper skillRelMapper;
    private final WorkerScopeMapper scopeMapper;
    private final SkillMapper skillMapper;
    private final AuthService authService;
    private final DispatchPolicy dispatchPolicy;

    public WorkerService(WorkerMapper workerMapper, UserMapper userMapper,
                         WorkerProfileMapper profileMapper, WorkerSkillMapper skillRelMapper,
                         WorkerScopeMapper scopeMapper, SkillMapper skillMapper,
                         AuthService authService, DispatchPolicy dispatchPolicy) {
        this.workerMapper = workerMapper;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
        this.skillRelMapper = skillRelMapper;
        this.scopeMapper = scopeMapper;
        this.skillMapper = skillMapper;
        this.authService = authService;
        this.dispatchPolicy = dispatchPolicy;
    }

    public PageVO<WorkerVO> page(String keyword, Long skillId, Boolean onDuty, long page, long pageSize) {
        IPageFill p = selectFilledPage(keyword, skillId, onDuty, page, pageSize);
        return new PageVO<>(p.list, p.page.getTotal());
    }

    public WorkerVO detail(Long userId) {
        IPageFill p = selectFilledPage(null, null, null, 1, Integer.MAX_VALUE);
        return p.list.stream().filter(v -> v.getId().equals(userId)).findFirst()
                .orElseThrow(() -> BusinessException.notFound("维修工不存在"));
    }

    private record IPageFill(IPage<WorkerVO> page, List<WorkerVO> list) {
    }

    private IPageFill selectFilledPage(String keyword, Long skillId, Boolean onDuty,
                                       long page, long pageSize) {
        IPage<WorkerVO> p = workerMapper.selectWorkerPage(
                new Page<>(page, Math.min(pageSize, 500)), keyword, onDuty, skillId);
        List<WorkerVO> list = p.getRecords();
        if (!list.isEmpty()) {
            fillRelations(list);
        }
        return new IPageFill(p, list);
    }

    /** 批量补充 技能 / 负责校区 / 当前负载 */
    private void fillRelations(List<WorkerVO> list) {
        List<Long> ids = list.stream().map(WorkerVO::getId).toList();

        Map<Long, List<Long>> skillIds = new HashMap<>();
        for (WorkerSkill ws : skillRelMapper.selectList(new LambdaQueryWrapper<WorkerSkill>()
                .in(WorkerSkill::getUserId, ids))) {
            skillIds.computeIfAbsent(ws.getUserId(), k -> new ArrayList<>()).add(ws.getSkillId());
        }
        Map<Long, Skill> skillMap = skillIds.values().stream().flatMap(List::stream).distinct()
                .collect(Collectors.toMap(Function.identity(),
                        id -> skillMapper.selectById(id), (a, b) -> a));

        Map<Long, List<String>> campusCodes = new HashMap<>();
        for (WorkerScope s : scopeMapper.selectList(new LambdaQueryWrapper<WorkerScope>()
                .in(WorkerScope::getUserId, ids))) {
            campusCodes.computeIfAbsent(s.getUserId(), k -> new ArrayList<>()).add(s.getCampus());
        }

        Map<Long, Long> loads = new HashMap<>();
        for (Map<String, Object> row : workerMapper.countLoad(ids)) {
            loads.put(((Number) row.get("workerId")).longValue(), ((Number) row.get("cnt")).longValue());
        }

        for (WorkerVO v : list) {
            for (Long sid : skillIds.getOrDefault(v.getId(), List.of())) {
                Skill s = skillMap.get(sid);
                if (s != null) {
                    v.getSkillIds().add(sid);
                    v.getSkills().add(s.getName());
                }
            }
            for (String code : campusCodes.getOrDefault(v.getId(), List.of())) {
                v.getCampusCodes().add(code);
                v.getCampuses().add(Campus.labelOf(code));
            }
            v.setCurrentLoad(loads.getOrDefault(v.getId(), 0L).intValue());
        }
    }

    @Transactional
    public WorkerVO create(WorkerCreateReq req) {
        if (req.loadLimit() != null && (req.loadLimit() < 1 || req.loadLimit() > 20)) {
            throw BusinessException.param("负载上限取值 1-20");
        }
        SysUser u = new SysUser();
        u.setJobNo(req.jobNo());
        u.setName(req.name());
        u.setPhone(req.phone());
        u.setRole(Role.REPAIRER);
        u.setStatus(1);
        u.setPasswordHash(authService.encoder().encode(req.password()));
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getJobNo, req.jobNo()));
        if (count != null && count > 0) {
            throw BusinessException.param("工号已存在: " + req.jobNo());
        }
        userMapper.insert(u);

        WorkerProfile profile = new WorkerProfile();
        profile.setUserId(u.getId());
        profile.setOnDuty(true);
        profile.setLoadLimit(req.loadLimit() == null ? 5 : req.loadLimit());
        profile.setScore(new BigDecimal("5.00"));
        profileMapper.insert(profile);

        replaceSkills(u.getId(), req.skillIds());
        replaceCampuses(u.getId(), req.campusCodes());
        return detail(u.getId());
    }

    @Transactional
    public WorkerVO update(Long userId, String name, String phone, List<Long> skillIds,
                           List<String> campusCodes, Integer loadLimit, Boolean onDuty) {
        SysUser u = userMapper.selectById(userId);
        if (u == null || u.getRole() != Role.REPAIRER) {
            throw BusinessException.notFound("维修工不存在");
        }
        if (name != null) {
            u.setName(name);
        }
        if (phone != null) {
            u.setPhone(phone);
        }
        userMapper.updateById(u);

        WorkerProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            profile = new WorkerProfile();
            profile.setUserId(userId);
            profile.setScore(new BigDecimal("5.00"));
            profile.setOnDuty(true);
            profile.setLoadLimit(5);
            profileMapper.insert(profile);
        }
        if (loadLimit != null) {
            if (loadLimit < 1 || loadLimit > 20) {
                throw BusinessException.param("负载上限取值 1-20");
            }
            profile.setLoadLimit(loadLimit);
        }
        if (onDuty != null) {
            profile.setOnDuty(onDuty);
        }
        profileMapper.updateById(profile);

        if (skillIds != null) {
            replaceSkills(userId, skillIds);
        }
        if (campusCodes != null) {
            replaceCampuses(userId, campusCodes);
        }
        return detail(userId);
    }

    public void setOnDuty(Long userId, boolean onDuty) {
        WorkerProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            throw BusinessException.notFound("维修工档案不存在");
        }
        profile.setOnDuty(onDuty);
        profileMapper.updateById(profile);
    }

    private void replaceSkills(Long userId, List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            throw BusinessException.param("请至少选择一项技能");
        }
        if (skillMapper.selectBatchIds(skillIds).size() != skillIds.stream().distinct().count()) {
            throw BusinessException.param("存在无效的技能选项");
        }
        skillRelMapper.delete(new LambdaQueryWrapper<WorkerSkill>().eq(WorkerSkill::getUserId, userId));
        for (Long sid : skillIds.stream().distinct().toList()) {
            WorkerSkill ws = new WorkerSkill();
            ws.setUserId(userId);
            ws.setSkillId(sid);
            ws.setLevel(SkillLevel.PRIMARY);
            skillRelMapper.insert(ws);
        }
    }

    /** 负责校区：只接受 Campus 枚举中的 code，不再依赖位置树节点 */
    private void replaceCampuses(Long userId, List<String> campusCodes) {
        if (campusCodes == null || campusCodes.isEmpty()) {
            throw BusinessException.param("请至少选择一个负责校区");
        }
        for (String code : campusCodes) {
            if (Campus.of(code) == null) {
                throw BusinessException.param("存在无效的校区选项: " + code);
            }
        }
        scopeMapper.delete(new LambdaQueryWrapper<WorkerScope>().eq(WorkerScope::getUserId, userId));
        for (String code : campusCodes.stream().distinct().toList()) {
            WorkerScope s = new WorkerScope();
            s.setUserId(userId);
            s.setCampus(code);
            scopeMapper.insert(s);
        }
    }

    // ------------------------------------------------------------------
    // 派单资格与推荐（规则统一收敛到 DispatchPolicy）
    // ------------------------------------------------------------------

    public List<EligibleWorkerVO> eligibleWorkersForOrder(RepairOrder order) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, Role.REPAIRER)
                .eq(SysUser::getStatus, 1));
        if (users.isEmpty()) {
            return List.of();
        }
        List<DispatchPolicy.Context> contexts = dispatchPolicy.loadContexts(users);
        Set<Long> cooling = dispatchPolicy.coolingWorkerIds(order.getId());

        List<EligibleWorkerVO> result = new ArrayList<>(contexts.size());
        for (DispatchPolicy.Context c : contexts) {
            List<String> reasons = dispatchPolicy.failReasons(c, order, cooling, true);
            boolean eligible = reasons.isEmpty();
            DispatchPolicy.Score score = eligible ? dispatchPolicy.score(c, order) : null;
            result.add(new EligibleWorkerVO(
                    c.workerId(), c.name(),
                    List.copyOf(c.skills().keySet()),
                    c.campuses().stream().map(Campus::labelOf).toList(),
                    c.load(), c.loadLimit(), c.onDuty(), c.score(),
                    eligible, reasons,
                    score == null ? null : score.total(),
                    score == null ? Map.of() : score.breakdown(),
                    score == null ? List.of() : score.reasons()));
        }
        result.sort((a, b) -> {
            if (a.eligible() != b.eligible()) {
                return a.eligible() ? -1 : 1;
            }
            double sa = a.matchScore() == null ? 0 : a.matchScore();
            double sb = b.matchScore() == null ? 0 : b.matchScore();
            if (Double.compare(sb, sa) != 0) {
                return Double.compare(sb, sa);
            }
            return Integer.compare(a.currentLoad(), b.currentLoad());
        });
        return result;
    }

    /** 指定维修工对某工单的不可派原因；checkLoad=false 时忽略负载（用于详情预览） */
    public List<String> failReasons(Long workerId, RepairOrder order, boolean checkLoad) {
        SysUser u = userMapper.selectById(workerId);
        if (u == null || u.getRole() != Role.REPAIRER
                || u.getStatus() == null || u.getStatus() != 1) {
            return List.of("账号不可用");
        }
        DispatchPolicy.Context c = dispatchPolicy.loadContext(u);
        return dispatchPolicy.failReasons(c, order, dispatchPolicy.coolingWorkerIds(order.getId()), checkLoad);
    }

    public boolean isEligible(Long workerId, RepairOrder order) {
        return failReasons(workerId, order, true).isEmpty();
    }

    public boolean canPreview(Long workerId, RepairOrder order) {
        return failReasons(workerId, order, false).isEmpty();
    }

    /** 当前登录维修工对某工单的自助承接资格 */
    public List<String> selfFailReasons(RepairOrder order) {
        return failReasons(AuthContext.userId(), order, true);
    }

    /** 派单硬校验：不合格抛 40001（附原因） */
    public void assertEligible(Long workerId, RepairOrder order) {
        List<String> reasons = failReasons(workerId, order, true);
        if (!reasons.isEmpty()) {
            throw BusinessException.param("该维修工不可派单: " + String.join("；", reasons));
        }
    }

    /** 抢单大厅：给列表补匹配分与推荐理由（当前登录维修工视角） */
    public void fillGrabMatch(List<OrderListVO> vos, List<RepairOrder> orders) {
        if (vos.isEmpty() || orders.isEmpty()) {
            return;
        }
        SysUser u = userMapper.selectById(AuthContext.userId());
        if (u == null) {
            return;
        }
        DispatchPolicy.Context c = dispatchPolicy.loadContext(u);
        Map<Long, RepairOrder> byId = orders.stream()
                .collect(Collectors.toMap(RepairOrder::getId, Function.identity(), (a, b) -> a));
        for (OrderListVO v : vos) {
            RepairOrder o = byId.get(v.getId());
            if (o == null) {
                continue;
            }
            DispatchPolicy.Score s = dispatchPolicy.score(c, o);
            v.setMatchScore(s.total());
            v.setMatchReasons(s.reasons());
        }
    }
}
