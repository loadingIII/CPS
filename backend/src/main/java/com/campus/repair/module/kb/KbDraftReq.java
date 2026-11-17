package com.campus.repair.module.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 草稿编辑：只有 status=2 的草稿允许改正文，已发布文章仍只允许改标题/启停。
 */
public record KbDraftReq(@Size(max = 128, message = "标题需 128 字以内") String title,
                         @NotBlank(message = "正文不能为空") String content) {
}
