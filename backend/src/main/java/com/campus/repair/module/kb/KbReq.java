package com.campus.repair.module.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库文章维护：只允许改标题与启用状态，正文由上传的 Markdown 决定。
 */
public record KbReq(@NotBlank(message = "请填写标题")
                    @Size(max = 128, message = "标题需 128 字以内") String title,
                    Boolean status) {
}
