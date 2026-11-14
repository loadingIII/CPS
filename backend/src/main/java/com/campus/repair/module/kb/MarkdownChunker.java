package com.campus.repair.module.kb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 切分：先按标题分节，再对每节做滑动窗口。
 *
 * <p>与 {@code CPS/scripts/ingest_kb.py} 保持同一策略（窗口 500 / 重叠 80，
 * 尽量在段落、句号处断开），这样管理端文章和 md 文件产出的块风格一致。</p>
 */
public final class MarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.*)$");

    private MarkdownChunker() {
    }

    public record Chunk(String section, int index, String text, int start, int end) {
    }

    private record Section(String heading, String text) {
    }

    public static List<Chunk> split(String content, int size, int overlap) {
        List<Chunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        int window = Math.max(120, size);
        int step = Math.max(0, Math.min(overlap, window - 40));

        for (Section section : splitSections(content)) {
            for (int[] range : windows(section.text(), window, step)) {
                String text = section.text().substring(range[0], range[1]).strip();
                if (text.isEmpty()) {
                    continue;
                }
                chunks.add(new Chunk(section.heading(), chunks.size(), text, range[0], range[1]));
            }
        }
        return chunks;
    }

    private static List<Section> splitSections(String content) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING.matcher(content);
        List<int[]> headings = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        while (matcher.find()) {
            headings.add(new int[]{matcher.start(), matcher.end()});
            titles.add(matcher.group(2).trim());
        }

        if (headings.isEmpty()) {
            String text = content.strip();
            if (!text.isEmpty()) {
                sections.add(new Section("", text));
            }
            return sections;
        }

        // 第一个标题之前的内容（如果有）单独成段
        String preamble = content.substring(0, headings.get(0)[0]).strip();
        if (!preamble.isEmpty()) {
            sections.add(new Section("", preamble));
        }

        for (int i = 0; i < headings.size(); i++) {
            int start = headings.get(i)[1];
            int end = i + 1 < headings.size() ? headings.get(i + 1)[0] : content.length();
            String text = content.substring(start, end).strip();
            if (!text.isEmpty()) {
                sections.add(new Section(titles.get(i), text));
            }
        }
        return sections;
    }

    /** 滑动窗口：尽量在段落/句号/换行处断开，避免把一句话切成两半 */
    private static List<int[]> windows(String text, int size, int overlap) {
        List<int[]> ranges = new ArrayList<>();
        int length = text.length();
        if (length <= size) {
            ranges.add(new int[]{0, length});
            return ranges;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + size, length);
            if (end < length) {
                int lower = start + (int) (size * 0.65);
                int cut = Math.max(
                        text.lastIndexOf("\n\n", end),
                        Math.max(text.lastIndexOf("。", end), text.lastIndexOf("\n", end)));
                if (cut > lower) {
                    end = cut + 1;
                }
            }
            ranges.add(new int[]{start, end});
            if (end >= length) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return ranges;
    }
}
