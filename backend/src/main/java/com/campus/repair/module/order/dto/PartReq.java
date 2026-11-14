package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 配件登记（R2-F5-5）：完工前由承接维修工录入。
 */
public record PartReq(@NotBlank(message = "请填写配件名称")
                      @Size(max = 64, message = "配件名称需 64 字以内") String partName,
                      @Min(value = 1, message = "数量至少 1")
                      @Max(value = 99, message = "数量不能超过 99") Integer quantity) {
}
