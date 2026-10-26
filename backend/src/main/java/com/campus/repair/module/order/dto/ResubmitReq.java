package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResubmitReq(@NotBlank(message = "请补充故障描述")
                          @Size(min = 5, max = 1000, message = "故障描述需 5-1000 字") String description,
                          @NotEmpty(message = "请至少上传 1 张图片")
                          @Size(max = 3, message = "图片最多 3 张") List<String> imageUrls) {
}
