package com.campus.repair.module.kb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 上传解析：去掉 front-matter，正文原样保存；标题取文件名。
 *
 * <p>front-matter 里的 title / fault_type 不再参与入库——标题固定用文件名，
 * 知识库也不再按故障类型分类。</p>
 */
public final class MarkdownDocParser {

    private static final Pattern FRONT_MATTER =
            Pattern.compile("^---\\r?\\n[\\s\\S]*?\\r?\\n---\\r?\\n?");
    private static final Pattern EXT = Pattern.compile("\\.(md|markdown)$", Pattern.CASE_INSENSITIVE);

    private MarkdownDocParser() {
    }

    public record Parsed(String content) {
    }

    /** 去掉 front-matter，返回正文 */
    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.replace("\uFEFF", "");
        Matcher matcher = FRONT_MATTER.matcher(text);
        if (matcher.find()) {
            text = text.substring(matcher.end());
        }
        return new Parsed(text.strip());
    }

    /** 文件名 → 标题：去目录、去扩展名，空则「未命名文档」，最长 128 字 */
    public static String titleFromFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replaceAll("^.*[\\\\/]", "").trim();
        name = EXT.matcher(name).replaceAll("").trim();
        if (name.isEmpty()) {
            name = "未命名文档";
        }
        return name.length() > 128 ? name.substring(0, 128) : name;
    }
}
