package com.campus.repair.module.order.dto;

import com.campus.repair.module.order.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitReq(@NotNull(message = "请选择报修位置") Long locationId,
                        @NotBlank(message = "请选择故障类型") String faultType,
                        @NotNull(message = "请选择紧急程度") Urgency urgency,
                        @NotBlank(message = "请填写故障描述")
                        @Size(min = 5, max = 1000, message = "故障描述需 5-1000 字") String description,
                        @Size(max = 3, message = "图片最多 3 张") List<String> imageUrls,
                        /** 地图选点补充定位（选填，DB-02） */
                        String mapName,
                        Double mapLng,
                        Double mapLat,
                        /** 幂等 token：同 token 5 分钟内重复提交返回首次结果 */
                        String clientToken) {
}
