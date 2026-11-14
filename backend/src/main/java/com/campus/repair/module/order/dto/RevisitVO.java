package com.campus.repair.module.order.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 差评回访任务列表行（R2-F7-3）。
 */
@Data
public class RevisitVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String locationText;
    private Long workerId;
    private String workerName;
    private Integer rating;
    private String status;
    private String remark;
    private String evaluationContent;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
}
