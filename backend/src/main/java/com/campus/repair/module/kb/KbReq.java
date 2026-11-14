package com.campus.repair.module.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库文章维护（管理员）。
 */
public record KbReq(@NotBlank(message = "请填写标题")
                    @Size(max = 128, message = "标题需 128 字以内") String title,
                    @NotBlank(message = "请选择故障类型") String faultType,
                    @NotBlank(message = "请填写内容") String content,
                    Boolean status) {
}
