package com.campus.repair.module.kb;

/**
 * Markdown 导入结果。
 *
 * @param id      新建文章 id
 * @param title   标题（取自文件名）
 * @param chunks  写入向量库的切片数
 * @param indexed 是否成功写入向量库
 * @param warning 文章已保存但索引未完成时的提示，正常为 null
 */
public record KbImportResult(Long id, String title,
                             int chunks, boolean indexed, String warning) {
}
