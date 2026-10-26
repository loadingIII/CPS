package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("worker_scope")
public class WorkerScope {

    private Long userId;

    private Long placeId;

    /** AREA / BUILDING */
    private String scopeType;
}
