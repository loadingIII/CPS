package com.campus.repair.module.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.module.agent.dto.AgentChatResp;
import com.campus.repair.module.kb.KbArticle;
import com.campus.repair.module.kb.KbArticleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 关键词问答：未配置大模型、Chroma 不可用或调用异常时的降级路径。
 *
 * <p>沿用 R2 的做法——故障关键词定位类型，再对 MySQL 知识库做标题/正文加权检索，
 * 用文章步骤拼出回答。保证「没有密钥也能跑通整条链路」。</p>
 */
@Service
public class KeywordAgentFallback {

    private final KbArticleMapper kbArticleMapper;

    /** 最低相关分：正文单个泛词命中（1 分）不足以推荐文章 */
    private static final int MIN_SCORE = 3;

    /** 故障类型 → 常见口语关键词，用于识别用户描述 */
    private static final Map<String, List<String>> FAULT_TERMS = new LinkedHashMap<>();

    static {
        FAULT_TERMS.put("空调家电", List.of(
                "空调", "制冷", "制热", "外机", "遥控", "滤网", "风扇", "冰箱", "洗衣机", "热水器", "家电"));
        FAULT_TERMS.put("水电", List.of(
                "跳闸", "断电", "停电", "插座", "开关", "灯", "水管", "漏水", "水龙头", "马桶", "下水", "热水"));
        FAULT_TERMS.put("木工门窗", List.of(
                "门锁", "门", "窗", "玻璃", "合页", "抽屉", "柜", "床", "桌", "椅", "锁"));
        FAULT_TERMS.put("网络多媒体", List.of(
                "网络", "网线", "wifi", "wi-fi", "投影", "讲台", "电脑", "多媒体", "屏幕", "音响", "麦克风"));
        FAULT_TERMS.put("电梯", List.of("电梯", "轿厢", "困人", "楼层"));
        FAULT_TERMS.put("实验设备", List.of("实验", "仪器", "设备", "机房"));
    }

    public KeywordAgentFallback(KbArticleMapper kbArticleMapper) {
        this.kbArticleMapper = kbArticleMapper;
    }

    public AgentChatResp answer(String message) {
        if (isGreeting(message)) {
            return new AgentChatResp(
                    "你好，我是报修小助手。描述一下故障现象，我会先帮你查知识库；如果解决不了，再帮你转到报修。",
                    List.of(), null);
        }

        List<KbArticle> articles = kbArticleMapper.selectList(new LambdaQueryWrapper<KbArticle>()
                .eq(KbArticle::getStatus, 1)
                .orderByDesc(KbArticle::getUpdatedAt));
        if (articles.isEmpty()) {
            return fallback(null);
        }

        String bestType = classify(message);
        Map<Long, Integer> scores = new HashMap<>();
        for (KbArticle article : articles) {
            scores.put(article.getId(), score(article, message));
        }
        List<KbArticle> top = articles.stream()
                .filter(a -> scores.getOrDefault(a.getId(), 0) >= MIN_SCORE)
                .sorted((a, b) -> Integer.compare(scores.get(b.getId()), scores.get(a.getId())))
                .limit(2)
                .toList();

        if (top.isEmpty()) {
            return fallback(bestType);
        }

        KbArticle first = top.get(0);
        String steps = Arrays.stream(nullToEmpty(first.getContent()).split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .collect(Collectors.joining("\n"));
        String answer = "我先查到《" + first.getTitle() + "》，可以按这几步试试：\n"
                + steps
                + "\n如果处理后仍未解决，直接提交报修"
                + (bestType == null ? "。" : "，我可以帮你把类型选成「" + bestType + "」。");

        List<AgentChatResp.ArticleRef> refs = top.stream()
                .map(a -> new AgentChatResp.ArticleRef(
                        a.getId(), a.getTitle(), bestType, excerpt(a.getContent())))
                .collect(Collectors.toList());
        return new AgentChatResp(answer, refs, bestType);
    }

    /** 选出与描述最匹配的故障类型；没有命中返回 null */
    public String classify(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String text = message.toLowerCase();
        String best = null;
        int bestHits = 0;
        for (Map.Entry<String, List<String>> entry : FAULT_TERMS.entrySet()) {
            int hits = 0;
            for (String term : entry.getValue()) {
                if (text.contains(term)) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = entry.getKey();
            }
        }
        return best;
    }

    public boolean isGreeting(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.length() <= 6 && (text.contains("你好") || text.contains("您好")
                || text.contains("在吗") || text.contains("hi") || text.contains("hello"));
    }

    /** 标题命中权重 3、正文命中 1；知识库已不分类别，只用关键词相关性排序 */
    private int score(KbArticle article, String message) {
        String title = nullToEmpty(article.getTitle());
        String content = nullToEmpty(article.getContent());

        int score = 0;
        for (List<String> terms : FAULT_TERMS.values()) {
            for (String term : terms) {
                if (!message.contains(term)) {
                    continue;
                }
                if (title.contains(term)) {
                    score += 3;
                }
                if (content.contains(term)) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private AgentChatResp fallback(String type) {
        String answer;
        if (type == null) {
            answer = "我暂时没找到完全匹配的教程。你可以再补充：哪个校区、什么设施、具体现象"
                    + "（如漏水、跳闸、不制冷、打不开），我再帮你判断；也可以直接提交报修。";
        } else {
            answer = "我暂时没有「" + type + "」的处理教程。建议直接提交报修，我可以帮你把类型选成「"
                    + type + "」。";
        }
        return new AgentChatResp(answer, List.of(), type);
    }

    private String excerpt(String content) {
        String oneLine = nullToEmpty(content).replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 供健康检查与统计使用：已启用文章数 */
    public long publishedCount() {
        Long count = kbArticleMapper.selectCount(new LambdaQueryWrapper<KbArticle>()
                .eq(KbArticle::getStatus, 1));
        return count == null ? 0 : count;
    }

    /** 故障类型列表，供提示词或前端下拉复用 */
    public static List<String> faultTypes() {
        return new ArrayList<>(FAULT_TERMS.keySet());
    }
}
