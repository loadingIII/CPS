package com.campus.repair.module.worker;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 维修工列表/详情视图。
 */
@Data
public class WorkerVO {

    private Long id;
    private String jobNo;
    private String name;
    private String phone;
    private Boolean onDuty;
    private Integer loadLimit;
    private BigDecimal score;
    private Integer currentLoad;

    private List<Long> skillIds = new ArrayList<>();
    private List<String> skills = new ArrayList<>();

    private List<Long> scopeIds = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
}
