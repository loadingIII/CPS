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

    /** 负责校区 code 列表 */
    private List<String> campusCodes = new ArrayList<>();
    /** 负责校区中文名列表 */
    private List<String> campuses = new ArrayList<>();
}
