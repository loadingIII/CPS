package com.campus.repair.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FinishReq(@NotBlank(message = "请填写维修说明")
                        @Size(min = 10, max = 500, message = "维修说明需 10-500 字") String summary,
                        @NotEmpty(message = "请上传 1-3 张完工照片")
                        @Size(max = 3, message = "完工照片最多 3 张") List<String> finishImageUrls) {
}
