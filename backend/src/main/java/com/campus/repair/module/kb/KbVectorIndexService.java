package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.module.agent.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端「维修知识库」→ Chroma 的索引同步。
 *
 * <p>文章的增删改都会同步向量库：先按 {@code kb_id} 删旧块，再按滑动窗口切分写入新块，
 * 因此编辑文章不会留下过期内容。停用或内容为空等价于删除。</p>
 *
 * <p>元数据里带 {@code kb_id}，检索命中后可以直接反查 MySQL 文章，
 * 不再依赖标题匹配。</p>
 */
@Service
public class KbVectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(KbVectorIndexService.class);

    /** 一次向量化的批大小 */
    private static final int EMBED_BATCH = 16;

    private final KbArticleMapper articleMapper;
    private final AgentProperties props;
    private final ObjectProvider<ChromaApi> chromaProvider;
    private final ObjectProvider<EmbeddingModel> embeddingProvider;

    public KbVectorIndexService(KbArticleMapper articleMapper,
                                AgentProperties props,
                                ObjectProvider<ChromaApi> chromaProvider,
                                ObjectProvider<EmbeddingModel> embeddingProvider) {
        this.articleMapper = articleMapper;
        this.props = props;
        this.chromaProvider = chromaProvider;
        this.embeddingProvider = embeddingProvider;
    }

    public boolean available() {
        return chromaProvider.getIfAvailable() != null && embeddingProvider.getIfAvailable() != null;
    }

    /**
     * 重建单篇文章的索引，返回写入块数。
     * 停用或空内容会删除该文章的所有块并返回 0。
     */
    public int index(KbArticle article) {
        if (article == null || article.getId() == null || !available()) {
            return 0;
        }
        if (article.getStatus() == null || article.getStatus() != 1
                || !StringUtils.hasText(article.getContent())) {
            remove(article.getId());
            return 0;
        }

        ChromaApi api = chromaProvider.getIfAvailable();
        String collectionId = ensureCollectionId(api);
        deleteByKbId(api, collectionId, article.getId());

        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(
                article.getContent(),
                props.getRag().getChunkSize(),
                props.getRag().getChunkOverlap());
        if (chunks.isEmpty()) {
            return 0;
        }

        EmbeddingModel model = embeddingProvider.getIfAvailable();
        List<String> ids = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        for (MarkdownChunker.Chunk chunk : chunks) {
            ids.add("kb:" + article.getId() + ":" + String.format("%04d", chunk.index()));
            documents.add(chunk.text());
            metadatas.add(metadata(article, chunk));
        }

        List<float[]> vectors = new ArrayList<>();
        for (int start = 0; start < documents.size(); start += EMBED_BATCH) {
            int end = Math.min(start + EMBED_BATCH, documents.size());
            vectors.addAll(model.embed(documents.subList(start, end)));
        }

        api.upsertEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.AddEmbeddingsRequest(ids, vectors, metadatas, documents));
        log.info("[KB] 文章 #{}《{}》写入 {} 块", article.getId(), article.getTitle(), chunks.size());
        return chunks.size();
    }

    /** 删除某篇文章的全部块 */
    public void remove(Long kbId) {
        if (kbId == null || !available()) {
            return;
        }
        try {
            ChromaApi api = chromaProvider.getIfAvailable();
            String collectionId = ensureCollectionId(api);
            deleteByKbId(api, collectionId, kbId);
        } catch (Exception e) {
            log.warn("[KB] 删除文章 #{} 的向量块失败：{}", kbId, e.getMessage());
        }
    }

    /**
     * 连通性预检：让「重建索引」在 Chroma 没起时直接报错，
     * 而不是逐篇失败后返回一堆 failed。
     */
    public void checkConnection() {
        ChromaApi api = chromaProvider.getIfAvailable();
        if (api == null) {
            throw new IllegalStateException("向量库未启用");
        }
        ensureCollectionId(api);
    }

    /** 全量重建：启用文章写入，停用文章清掉旧块 */
    public SyncReport reindexAll() {
        List<KbArticle> articles = articleMapper.selectList(new LambdaQueryWrapper<KbArticle>()
                .orderByAsc(KbArticle::getId));
        int indexed = 0;
        int chunks = 0;
        int failed = 0;
        for (KbArticle article : articles) {
            try {
                if (article.getStatus() != null && article.getStatus() == 1) {
                    chunks += index(article);
                    indexed++;
                } else {
                    remove(article.getId());
                }
            } catch (Exception e) {
                failed++;
                log.warn("[KB] 文章 #{} 同步失败：{}", article.getId(), e.getMessage());
            }
        }
        log.info("[KB] 全量重建完成：文章 {} 篇（成功 {}），块 {}，失败 {}",
                articles.size(), indexed, chunks, failed);
        return new SyncReport(articles.size(), indexed, chunks, failed);
    }

    /**
     * 首次部署时集合可能还不存在：自动建一个余弦距离的集合，
     * 这样只用管理端、不跑入库脚本也能工作。
     */
    public String ensureCollectionId(ChromaApi api) {
        String name = props.getChroma().getCollection();
        try {
            return api.getCollection(tenant(), database(), name).id();
        } catch (Exception notFound) {
            try {
                api.createCollection(tenant(), database(),
                        new ChromaApi.CreateCollectionRequest(name, Map.of("hnsw:space", "cosine")));
                log.info("[KB] 自动创建集合 {}", name);
            } catch (Exception conflict) {
                // 并发创建时可能已被别人建好，继续取一次
            }
            return api.getCollection(tenant(), database(), name).id();
        }
    }

    /** 启动时若集合还是空的，自动把已有文章灌进去，避免「管理端有文章但助手查不到」 */
    @EventListener(ApplicationReadyEvent.class)
    public void autoIndexOnStartup() {
        if (!available()) {
            return;
        }
        try {
            ChromaApi api = chromaProvider.getIfAvailable();
            String collectionId = ensureCollectionId(api);
            Long count = api.countEmbeddings(tenant(), database(), collectionId);
            Long published = articleMapper.selectCount(new LambdaQueryWrapper<KbArticle>()
                    .eq(KbArticle::getStatus, 1));
            if ((count == null || count == 0) && published != null && published > 0) {
                SyncReport report = reindexAll();
                log.info("[KB] 启动自动同步：{} 篇 / {} 块", report.indexed(), report.chunks());
            }
        } catch (Exception e) {
            log.info("[KB] 跳过启动同步：{}", e.getMessage());
        }
    }

    private void deleteByKbId(ChromaApi api, String collectionId, Long kbId) {
        api.deleteEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.DeleteEmbeddingsRequest(null, Map.of("kb_id", kbId)));
    }

    private Map<String, Object> metadata(KbArticle article, MarkdownChunker.Chunk chunk) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kb_id", article.getId());
        meta.put("doc_id", "kb:" + article.getId());
        meta.put("source", "kb_article");
        meta.put("title", article.getTitle() == null ? "" : article.getTitle());
        meta.put("section", chunk.section() == null ? "" : chunk.section());
        meta.put("chunk_index", chunk.index());
        meta.put("char_start", chunk.start());
        meta.put("char_end", chunk.end());
        meta.put("updated_at", article.getUpdatedAt() == null ? "" : article.getUpdatedAt().toString());
        return meta;
    }

    private String tenant() {
        return props.getChroma().getTenant();
    }

    private String database() {
        return props.getChroma().getDatabase();
    }

    /** 全量重建结果 */
    public record SyncReport(int articles, int indexed, int chunks, int failed) {
    }
}
