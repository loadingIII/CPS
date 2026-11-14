package com.campus.repair.module.order.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提交前查重结果（R2-F2-5）。
 *
 * <p>只暴露判断重复所需的最小字段：不返回描述、报修人姓名等他人隐私信息；
 * mine=true 表示该单是当前报修人自己的，前端可安全跳转详情。
 */
@Data
public class DuplicateOrderVO {

    private Long id;
    private String orderNo;
    private String locationText;
    private String faultType;
    private String status;
    private String statusLabel;
    private LocalDateTime submittedAt;
    /** 是否当前报修人自己的工单 */
    private boolean mine;
}
