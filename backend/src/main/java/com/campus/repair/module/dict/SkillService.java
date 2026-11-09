package com.campus.repair.module.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.module.order.RepairOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SkillService {

    private final SkillMapper skillMapper;
    private final RepairOrderMapper repairOrderMapper;

    public SkillService(SkillMapper skillMapper, RepairOrderMapper repairOrderMapper) {
        this.skillMapper = skillMapper;
        this.repairOrderMapper = repairOrderMapper;
    }

    public List<Skill> list(boolean includeDisabled) {
        return skillMapper.selectList(new LambdaQueryWrapper<Skill>()
                .eq(!includeDisabled, Skill::getEnabled, true)
                .orderByAsc(Skill::getId));
    }

    public Skill create(String name) {
        if (!StringUtils.hasText(name)) {
            throw BusinessException.param("技能名称不能为空");
        }
        if (name.length() > 32) {
            throw BusinessException.param("技能名称过长");
        }
        Long count = skillMapper.selectCount(new LambdaQueryWrapper<Skill>().eq(Skill::getName, name));
        if (count != null && count > 0) {
            throw BusinessException.param("技能已存在: " + name);
        }
        Skill s = new Skill();
        s.setName(name);
        s.setEnabled(true);
        skillMapper.insert(s);
        return s;
    }

    public void update(Long id, String name, Boolean enabled) {
        Skill s = skillMapper.selectById(id);
        if (s == null) {
            throw BusinessException.notFound("技能不存在");
        }
        if (Boolean.FALSE.equals(enabled) && Boolean.TRUE.equals(s.getEnabled())) {
            Long used = repairOrderMapper.selectCount(new LambdaQueryWrapper<com.campus.repair.module.order.RepairOrder>()
                    .eq(com.campus.repair.module.order.RepairOrder::getFaultType, s.getName()));
            if (used != null && used > 0) {
                throw BusinessException.param("该技能已被工单引用，不能禁用");
            }
        }
        if (StringUtils.hasText(name)) {
            s.setName(name);
        }
        if (enabled != null) {
            s.setEnabled(enabled);
        }
        skillMapper.updateById(s);
    }
}
