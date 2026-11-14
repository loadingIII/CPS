package com.campus.repair.module.agent.rag;

import com.campus.repair.module.agent.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索：查询 → Chroma topK → 阈值过滤 → 拼上下文 + 收集引用。
 *
 * <p>集合由入库脚本创建；集合不存在或 Chroma 不可达时不抛异常，
 * 返回空结果，由上层走「没查到资料」的降级话术，避免整轮问答 500。</p>
 */
@Service
public class RagRetriever {

    private static final Logger log = LoggerFactory.getLogger(RagRetriever.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final AgentProperties props;

    public RagRetriever(ObjectProvider<VectorStore> vectorStoreProvider, AgentProperties props) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.props = props;
    }

    public RagResult retrieve(String query) {
        if (!StringUtils.hasText(query)) {
            return RagResult.empty();
        }

        List<Document> docs;
        try {
            // VectorStore 是懒加载 Bean：Chroma 未启动时在这里才会抛错，被下面统一兜住
            VectorStore store = vectorStoreProvider.getIfAvailable();
            if (store == null) {
                return RagResult.empty();
            }
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(props.getRag().getTopK())
                    // SearchRequest 只接受 [0,1]，配置写错时钳制而不是整轮检索失败
                    .similarityThreshold(Math.max(0d, Math.min(1d,
                            props.getRag().getSimilarityThreshold())))
                    .build();
            docs = store.similaritySearch(request);
        } catch (Exception e) {
            log.warn("[RAG] 向量检索失败（集合 {}）：{}", props.getChroma().getCollection(), e.getMessage());
            return RagResult.empty();
        }
        if (docs == null || docs.isEmpty()) {
            return RagResult.empty();
        }

        int budget = props.getRag().getMaxContextChars();
        StringBuilder context = new StringBuilder();
        Map<String, RagResult.RagRef> refs = new LinkedHashMap<>();
        int index = 0;

        for (Document doc : docs) {
            String text = doc.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            Map<String, Object> meta = doc.getMetadata();
            String title = str(meta.get("title"), "未命名文档");
            String faultType = str(meta.get("fault_type"), "");
            String source = str(meta.get("source"), "");
            String docKey = str(meta.get("doc_id"), doc.getId());

            if (!refs.containsKey(docKey)) {
                refs.put(docKey, new RagResult.RagRef(
                        doc.getId(), title, faultType, source, excerpt(text),
                        toLong(meta.get("kb_id")), doc.getScore()));
            }

            String block = "【资料" + (++index) + "】" + title
                    + (faultType.isBlank() ? "" : "（" + faultType + "）") + "\n"
                    + text.strip() + "\n\n";
            if (context.length() + block.length() > budget) {
                int remain = budget - context.length();
                if (remain > 80) {
                    context.append(block, 0, remain);
                }
                break;
            }
            context.append(block);
        }

        return new RagResult(context.toString().strip(), new ArrayList<>(refs.values()));
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    /** 引用卡片摘要：压成一行，最多 60 字 */
    private String excerpt(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
