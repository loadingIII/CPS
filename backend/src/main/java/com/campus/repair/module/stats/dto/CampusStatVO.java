package com.campus.repair.module.stats.dto;

import java.util.List;

/**
 * 校区维度统计（R3）。
 *
 * <p>工单量按 submitted_at 落在窗口；完工量/按时率/平均处理时长按 completed_at 落在窗口。</p>
 */
public record CampusStatVO(String campus,
                           String campusName,
                           long total,
                           long completed,
                           Double onTimeRate,
                           Double avgHours,
                           Double urgentRate,
                           List<String> topFaultTypes) {
}
