package com.campus.repair.module.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 完工上报：维修说明、完工照片与配件消耗一次事务提交，
 * 避免「配件已落库但完工失败」留下孤儿记录。
 */
public record FinishReq(@NotBlank(message = "请填写维修说明")
                        @Size(min = 10, max = 500, message = "维修说明需 10-500 字") String summary,
                        @NotEmpty(message = "请上传 1-3 张完工照片")
                        @Size(max = 3, message = "完工照片最多 3 张") List<String> finishImageUrls,
                        @Size(max = 20, message = "配件最多登记 20 项")
                        List<@Valid PartReq> parts) {
}
