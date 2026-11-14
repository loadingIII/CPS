package com.campus.repair.module.order.dto;

import com.campus.repair.module.order.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 补充重报：驳回原因可能是类型/位置选错，因此允许连同位置、类型、紧急度一起修正。
 * 图片与首次提交一致，允许 0-3 张（原图由前端预填）。
 */
public record ResubmitReq(@NotBlank(message = "请选择校区") String campus,
                          @NotBlank(message = "请填写详细位置")
                          @Size(max = 200, message = "详细位置需 200 字以内") String locationDetail,
                          @NotBlank(message = "请选择故障类型") String faultType,
                          @NotNull(message = "请选择紧急程度") Urgency urgency,
                          @NotBlank(message = "请补充故障描述")
                          @Size(min = 5, max = 1000, message = "故障描述需 5-1000 字") String description,
                          @Size(max = 3, message = "图片最多 3 张") List<String> imageUrls) {
}
