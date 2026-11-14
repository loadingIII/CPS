package com.campus.repair.module.kb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 上传解析：取 front-matter 的 title / fault_type，正文去掉 front-matter。
 *
 * <p>与前端 {@code admin-web/src/utils/markdown.js} 规则一致：
 * front-matter 优先，其次一级标题，再退文件名。</p>
 */
public final class MarkdownDocParser {

    private static final Pattern FRONT_MATTER =
            Pattern.compile("^---\\r?\\n([\\s\\S]*?)\\r?\\n---\\r?\\n?");
    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    private static final Pattern EXT = Pattern.compile("\\.(md|markdown)$", Pattern.CASE_INSENSITIVE);

    private MarkdownDocParser() {
    }

    public record Parsed(String title, String faultType, String content) {
    }

    public static Parsed parse(String raw, String fileName) {
        String text = raw == null ? "" : raw.replace("\uFEFF", "");
        Map<String, String> meta = new LinkedHashMap<>();
        String body = text;

        Matcher matcher = FRONT_MATTER.matcher(text);
        if (matcher.find()) {
            for (String line : matcher.group(1).split("\\r?\\n")) {
                int idx = line.indexOf(':');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx).trim().toLowerCase();
                String value = line.substring(idx + 1).trim().replaceAll("^[\"']|[\"']$", "");
                if (!key.isEmpty() && !value.isEmpty()) {
                    meta.put(key, value);
                }
            }
            body = text.substring(matcher.end());
        }

        String content = body.strip();
        String title = meta.getOrDefault("title", "");
        if (title.isBlank()) {
            Matcher heading = HEADING.matcher(content);
            title = heading.find() ? heading.group(1).trim() : baseName(fileName);
        }
        if (title.length() > 128) {
            title = title.substring(0, 128);
        }
        return new Parsed(title, meta.getOrDefault("fault_type", ""), content);
    }

    private static String baseName(String fileName) {
        String name = fileName == null ? "" : fileName.replaceAll("^.*[\\\\/]", "");
        return EXT.matcher(name).replaceAll("");
    }
}
