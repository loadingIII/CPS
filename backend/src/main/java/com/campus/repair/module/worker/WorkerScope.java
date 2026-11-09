package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 维修工负责校区（一个维修工可负责多个校区） */
@Data
@TableName("worker_scope")
public class WorkerScope {

    private Long userId;

    /** 校区枚举 code（Campus.name()） */
    private String campus;
}
