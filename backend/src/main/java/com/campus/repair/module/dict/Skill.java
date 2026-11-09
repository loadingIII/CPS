package com.campus.repair.module.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Boolean enabled;
}
