package com.campus.repair.module.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
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
 * 智能报修助手：基于知识库检索的问答（RAG 雏形）。
 *
 * <p>不依赖外部大模型：先用故障关键词定位类型，再对启用中的知识库文章做
 * 标题/正文加权检索，取最相关的 1-2 篇，用文章步骤拼出回答，并把类型带回前端，
 * 支持一键转到报修表单。后续接入 LLM 时只需替换 answer 的生成部分。</p>
 */
@Service
public class AgentService {

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

    public AgentService(KbArticleMapper kbArticleMapper) {
        this.kbArticleMapper = kbArticleMapper;
    }

    public AgentChatResp chat(String raw) {
        String message = raw == null ? "" : raw.trim();
        if (message.isEmpty()) {
            throw BusinessException.param("请描述一下故障现象");
        }
        if (isGreeting(message)) {
            return new AgentChatResp(
                    "你好，我是报修小助手。描述一下故障现象，我会先帮你查知识库；如果解决不了，再帮你转到报修。",
                    List.of(), null);
        }

        List<KbArticle> articles = kbArticleMapper.selectList(new LambdaQueryWrapper<KbArticle>()
                .eq(KbArticle::getStatus, true)
                .orderByDesc(KbArticle::getUpdatedAt));
        if (articles.isEmpty()) {
            return fallback(null);
        }

        String bestType = bestFaultType(message);
        Map<Long, Integer> scores = new HashMap<>();
        for (KbArticle article : articles) {
            scores.put(article.getId(), score(article, message, bestType));
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
                + "\n如果处理后仍未解决，直接提交报修，我可以帮你把类型选成「" + first.getFaultType() + "」。";

        List<AgentChatResp.ArticleRef> refs = top.stream()
                .map(a -> new AgentChatResp.ArticleRef(
                        a.getId(), a.getTitle(), a.getFaultType(), excerpt(a.getContent())))
                .collect(Collectors.toList());
        return new AgentChatResp(answer, refs, first.getFaultType());
    }

    /** 标题命中权重 3、正文命中 1；类型直接命中与最佳类型再加权 */
    private int score(KbArticle article, String message, String bestType) {
        String fault = nullToEmpty(article.getFaultType());
        String title = nullToEmpty(article.getTitle());
        String content = nullToEmpty(article.getContent());

        int score = 0;
        if (!fault.isEmpty() && message.contains(fault)) {
            score += 10;
        }
        if (bestType != null && bestType.equals(fault)) {
            score += 4;
        }
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

    /** 选出与描述最匹配的故障类型；没有命中返回 null */
    private String bestFaultType(String message) {
        String best = null;
        int bestHits = 0;
        for (Map.Entry<String, List<String>> entry : FAULT_TERMS.entrySet()) {
            int hits = 0;
            for (String term : entry.getValue()) {
                if (message.contains(term)) {
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

    private boolean isGreeting(String message) {
        String text = message.toLowerCase();
        return text.length() <= 6 && (text.contains("你好") || text.contains("您好")
                || text.contains("在吗") || text.contains("hi") || text.contains("hello"));
    }

    private String excerpt(String content) {
        String oneLine = nullToEmpty(content).replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
