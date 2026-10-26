package com.campus.repair.module.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_log")
public class OrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String action;

    private Long operatorId;

    /** REPORTER/ADMIN/REPAIRER/SYS_ADMIN/SYSTEM */
    private String operatorRole;

    private String remark;

    private LocalDateTime createdAt;
}
