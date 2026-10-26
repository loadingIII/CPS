package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EvaluationReq(@NotNull(message = "请评分")
                            @Min(value = 1, message = "评分 1-5 星")
                            @Max(value = 5, message = "评分 1-5 星") Integer rating,
                            @Size(max = 500, message = "评价内容最长 500 字") String content,
                            /** R1 仅透传保存（逗号分隔），标签体系 R2 实现 */
                            List<String> tags) {
}
