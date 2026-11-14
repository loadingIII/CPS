package com.campus.repair.module.agent;

import com.campus.repair.module.kb.KbArticle;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.module.kb.KbArticleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 智能助手自检：快速区分「没配密钥」「Chroma 没起」「集合没建」 */
@Service
public class AgentHealthService {

    private static final Logger log = LoggerFactory.getLogger(AgentHealthService.class);

    private final AgentProperties props;
    private final AgentService agentService;
    private final ObjectProvider<ChromaApi> chromaApiProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final KbArticleMapper kbArticleMapper;

    public AgentHealthService(AgentProperties props,
                              AgentService agentService,
                              ObjectProvider<ChromaApi> chromaApiProvider,
                              ObjectProvider<EmbeddingModel> embeddingModelProvider,
                              KbArticleMapper kbArticleMapper) {
        this.props = props;
        this.agentService = agentService;
        this.chromaApiProvider = chromaApiProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.kbArticleMapper = kbArticleMapper;
    }

    public Map<String, Object> check(boolean deep) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean ai = agentService.aiAvailable();
        result.put("enabled", props.isEnabled());
        result.put("mode", ai ? "ai" : "fallback");
        result.put("configIssue", props.configIssue());
        result.put("fallbackArticles", countPublished());
        result.put("chat", modelInfo(props.getChatModel(), ai));
        result.put("embedding", embeddingInfo(deep, ai));
        result.put("chroma", chromaInfo(ai));
        return result;
    }

    private Map<String, Object> modelInfo(String model, boolean ai) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", ai);
        info.put("model", model == null ? "" : model);
        info.put("baseUrl", props.getBaseUrl() == null ? "" : props.getBaseUrl());
        return info;
    }

    private Map<String, Object> embeddingInfo(boolean deep, boolean ai) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", ai);
        info.put("model", props.getEmbeddingModel() == null ? "" : props.getEmbeddingModel());
        info.put("baseUrl", props.effectiveEmbeddingBaseUrl() == null ? "" : props.effectiveEmbeddingBaseUrl());
        info.put("endpoint", props.hasSeparateEmbeddingEndpoint() ? "separate" : "shared");
        info.put("expectedDimensions", props.getEmbeddingDimensions());
        if (!deep || !ai) {
            return info;
        }
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            info.put("ok", false);
            info.put("error", "EmbeddingModel 未装配");
            return info;
        }
        try {
            float[] vector = model.embed("连通性检查");
            info.put("ok", true);
            info.put("actualDimensions", vector == null ? 0 : vector.length);
        } catch (Exception e) {
            info.put("ok", false);
            info.put("error", e.getMessage());
        }
        return info;
    }

    private Map<String, Object> chromaInfo(boolean ai) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("baseUrl", props.chromaBaseUrl());
        info.put("collection", props.getChroma().getCollection());
        ChromaApi api = chromaApiProvider.getIfAvailable();
        if (!ai || api == null) {
            info.put("ok", false);
            info.put("error", "未启用向量检索");
            return info;
        }
        try {
            // Chroma 1.x 的统计接口只认集合 UUID，先用名字换 id
            ChromaApi.Collection collection = api.getCollection(
                    props.getChroma().getTenant(), props.getChroma().getDatabase(),
                    props.getChroma().getCollection());
            Long count = api.countEmbeddings(props.getChroma().getTenant(),
                    props.getChroma().getDatabase(), collection.id());
            info.put("ok", true);
            info.put("collectionId", collection.id());
            info.put("count", count == null ? 0 : count);
        } catch (Exception e) {
            log.debug("[Agent] Chroma 检查失败：{}", e.getMessage());
            info.put("ok", false);
            info.put("error", "Chroma 不可达或集合不存在，请先启动 Chroma 并执行入库脚本");
        }
        return info;
    }

    private long countPublished() {
        try {
            Long count = kbArticleMapper.selectCount(new LambdaQueryWrapper<KbArticle>()
                    .eq(KbArticle::getStatus, true));
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }
}
