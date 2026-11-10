package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("worker_skill")
public class WorkerSkill {

    private Long userId;

    private Long skillId;

    /** 主修 / 兼修（派单评分因子） */
    private SkillLevel level;
}
