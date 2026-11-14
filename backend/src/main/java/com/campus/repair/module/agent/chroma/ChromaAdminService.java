package com.campus.repair.module.agent.chroma;

import com.campus.repair.common.BusinessException;
import com.campus.repair.module.agent.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chroma 向量库管理封装，供后续管理端「文档管理」直接复用。
 *
 * <p>关键点：Chroma 1.x 的读写接口都要 **集合 UUID**，不是集合名。
 * 这里统一用 {@link #collectionId} 解析，避免调用方踩「Collection ID is not a valid UUIDv4」。</p>
 *
 * <p>集合本身由入库脚本创建，本类不负责建集合（重建集合会让后端缓存的 UUID 失效，
 * 需要重启后端）。{@link #clear()} 只删块、保留集合，清空后无需重启。</p>
 */
@Service
public class ChromaAdminService {

    private static final Logger log = LoggerFactory.getLogger(ChromaAdminService.class);

    private final AgentProperties props;
    private final ObjectProvider<ChromaApi> chromaProvider;
    private final ObjectProvider<EmbeddingModel> embeddingProvider;

    public ChromaAdminService(AgentProperties props,
                              ObjectProvider<ChromaApi> chromaProvider,
                              ObjectProvider<EmbeddingModel> embeddingProvider) {
        this.props = props;
        this.chromaProvider = chromaProvider;
        this.embeddingProvider = embeddingProvider;
    }

    /** 向量库是否可用（未配密钥 / 未启用时为 false） */
    public boolean available() {
        return chromaProvider.getIfAvailable() != null;
    }

    /** 集合概况：名称、UUID、条数 */
    public Map<String, Object> stats() {
        ChromaApi api = api();
        ChromaApi.Collection collection = requireCollection(api);
        Long count = api.countEmbeddings(tenant(), database(), collection.id());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", props.chromaBaseUrl());
        result.put("collection", collection.name());
        result.put("collectionId", collection.id());
        result.put("count", count == null ? 0 : count);
        return result;
    }

    /** 分页浏览块（不返回向量） */
    public List<ChunkVO> list(int limit, int offset) {
        ChromaApi api = api();
        String id = collectionId(api);

        ChromaApi.GetEmbeddingResponse response = api.getEmbeddings(
                tenant(), database(), id,
                new ChromaApi.GetEmbeddingsRequest(
                        null, null, Math.max(1, limit), Math.max(0, offset),
                        List.of(ChromaApi.QueryRequest.Include.DOCUMENTS,
                                ChromaApi.QueryRequest.Include.METADATAS)));

        List<ChunkVO> list = new ArrayList<>();
        if (response == null || response.ids() == null) {
            return list;
        }
        for (int i = 0; i < response.ids().size(); i++) {
            String text = response.documents() != null && response.documents().size() > i
                    ? response.documents().get(i) : "";
            Map<String, String> metadata = response.metadata() != null && response.metadata().size() > i
                    ? response.metadata().get(i) : Map.of();
            list.add(new ChunkVO(response.ids().get(i), text, metadata));
        }
        return list;
    }

    /** 批量写入：文本向量化后 upsert，同 id 覆盖 */
    public int upsert(List<ChunkReq> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw BusinessException.param("请至少提交一个块");
        }
        EmbeddingModel model = embeddingProvider.getIfAvailable();
        if (model == null) {
            throw BusinessException.conflict("向量模型未启用，请先在 .env 配置 AI_EMBEDDING_*");
        }
        ChromaApi api = api();
        String collectionId = collectionId(api);

        List<String> ids = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<String> documents = new ArrayList<>();

        for (ChunkReq chunk : chunks) {
            if (!StringUtils.hasText(chunk.id()) || !StringUtils.hasText(chunk.text())) {
                throw BusinessException.param("每个块都必须有 id 和 text");
            }
            ids.add(chunk.id());
            vectors.add(model.embed(chunk.text()));
            metadatas.add(normalizeMetadata(chunk));
            documents.add(chunk.text());
        }

        api.upsertEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.AddEmbeddingsRequest(ids, vectors, metadatas, documents));
        log.info("[Chroma] upsert {} 块到 {}", ids.size(), props.getChroma().getCollection());
        return ids.size();
    }

    /** 按块 id 删除，返回实际删除条数（Chroma 删除接口只回 HTTP 状态码，这里用前后计数差） */
    public int deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessException.param("请至少提供一个块 id");
        }
        ChromaApi api = api();
        String collectionId = collectionId(api);
        long before = count(api, collectionId);
        api.deleteEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.DeleteEmbeddingsRequest(ids, null));
        return (int) Math.max(0, before - count(api, collectionId));
    }

    /** 按文档删除（metadata.doc_id） */
    public int deleteByDocId(String docId) {
        if (!StringUtils.hasText(docId)) {
            throw BusinessException.param("docId 不能为空");
        }
        ChromaApi api = api();
        String collectionId = collectionId(api);
        long before = count(api, collectionId);
        api.deleteEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.DeleteEmbeddingsRequest(null, Map.of("doc_id", docId)));
        return (int) Math.max(0, before - count(api, collectionId));
    }

    /**
     * 清空集合内的块，保留集合本身（UUID 不变，后端无需重启）。
     * 每次取 500 个 id 删除，直到取空。
     */
    public int clear() {
        ChromaApi api = api();
        String collectionId = collectionId(api);
        long before = count(api, collectionId);

        while (true) {
            ChromaApi.GetEmbeddingResponse page = api.getEmbeddings(
                    tenant(), database(), collectionId,
                    new ChromaApi.GetEmbeddingsRequest(null, null, 500, 0, null));
            if (page == null || page.ids() == null || page.ids().isEmpty()) {
                break;
            }
            api.deleteEmbeddings(tenant(), database(), collectionId,
                    new ChromaApi.DeleteEmbeddingsRequest(page.ids(), null));
            if (page.ids().size() < 500) {
                break;
            }
        }
        int deleted = (int) Math.max(0, before - count(api, collectionId));
        log.info("[Chroma] 清空集合 {}，共删除 {} 块", props.getChroma().getCollection(), deleted);
        return deleted;
    }

    /* ==================== 内部 ==================== */

    private ChromaApi api() {
        ChromaApi api = chromaProvider.getIfAvailable();
        if (api == null) {
            throw BusinessException.conflict("向量库未启用，请先在 .env 配置 AI_EMBEDDING_* 并启动 Chroma");
        }
        return api;
    }

    private ChromaApi.Collection requireCollection(ChromaApi api) {
        try {
            ChromaApi.Collection collection = api.getCollection(tenant(), database(),
                    props.getChroma().getCollection());
            if (collection == null) {
                throw new IllegalStateException("集合不存在");
            }
            return collection;
        } catch (Exception e) {
            throw BusinessException.conflict("集合 " + props.getChroma().getCollection()
                    + " 不存在或 Chroma 不可达，请先执行入库脚本");
        }
    }

    private String collectionId(ChromaApi api) {
        return requireCollection(api).id();
    }

    private long count(ChromaApi api, String collectionId) {
        Long count = api.countEmbeddings(tenant(), database(), collectionId);
        return count == null ? 0 : count;
    }

    private String tenant() {
        return props.getChroma().getTenant();
    }

    private String database() {
        return props.getChroma().getDatabase();
    }

    /** Chroma 只接受标量元数据；缺 doc_id 时从 id 前缀推导 */
    private Map<String, Object> normalizeMetadata(ChunkReq chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (chunk.metadata() != null) {
            chunk.metadata().forEach((key, value) -> {
                if (key == null || value == null) {
                    return;
                }
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    metadata.put(key, value);
                } else {
                    metadata.put(key, String.valueOf(value));
                }
            });
        }
        if (!metadata.containsKey("doc_id")) {
            String id = chunk.id();
            int split = id.indexOf(':');
            metadata.put("doc_id", split > 0 ? id.substring(0, split) : id);
        }
        return metadata;
    }
}
