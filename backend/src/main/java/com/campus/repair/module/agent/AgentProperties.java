package com.campus.repair.module.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 智能助手配置：全部来自 .env（经 application.yml 占位符注入）。
 *
 * <p>只有 enabled=true 且 api-key、base-url、chat-model、embedding-model 均非空时，
 * 才会装配 Spring AI 相关 Bean；否则自动回落到关键词问答。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    /** 向量模型可单独配置端点与密钥；留空时回落到对话配置 */
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel;
    private int embeddingDimensions = 1024;
    private double temperature = 0.3;
    private int timeoutSeconds = 90;
    private Rag rag = new Rag();
    private Chroma chroma = new Chroma();

    /** 配置是否足以启用向量问答 */
    public boolean isUsable() {
        return enabled && configIssue().isEmpty();
    }

    /** 只判断对话模型是否可用（报修建议不需要 embedding） */
    public boolean chatUsable() {
        return enabled && StringUtils.hasText(baseUrl)
                && StringUtils.hasText(apiKey) && StringUtils.hasText(chatModel);
    }

    /** 返回第一处配置缺口，供自检与日志展示；为空表示配置完整 */
    public String configIssue() {
        if (!enabled) {
            return "AGENT_ENABLED=false";
        }
        if (!StringUtils.hasText(baseUrl)) {
            return "缺少 AI_BASE_URL";
        }
        if (!StringUtils.hasText(apiKey)) {
            return "缺少 AI_API_KEY";
        }
        if (!StringUtils.hasText(chatModel)) {
            return "缺少 AI_CHAT_MODEL";
        }
        if (!StringUtils.hasText(embeddingModel)) {
            return "缺少 AI_EMBEDDING_MODEL";
        }
        if (!StringUtils.hasText(effectiveEmbeddingBaseUrl())) {
            return "缺少 AI_EMBEDDING_BASE_URL";
        }
        if (!StringUtils.hasText(effectiveEmbeddingApiKey())) {
            return "配置了独立向量端点，但缺少 AI_EMBEDDING_API_KEY";
        }
        return "";
    }

    public String chromaBaseUrl() {
        return "http://" + chroma.getHost() + ":" + chroma.getPort();
    }

    /** 向量端点：优先专用配置，未填则沿用对话端点 */
    public String effectiveEmbeddingBaseUrl() {
        return StringUtils.hasText(embeddingBaseUrl) ? embeddingBaseUrl : baseUrl;
    }

    /**
     * 向量密钥：优先专用配置。
     * 一旦单独配了向量端点，就不再拿对话密钥去试——两家服务商的 key 不通用，
     * 否则只会在运行期收到 401。
     */
    public String effectiveEmbeddingApiKey() {
        if (StringUtils.hasText(embeddingApiKey)) {
            return embeddingApiKey;
        }
        return StringUtils.hasText(embeddingBaseUrl) ? "" : apiKey;
    }

    /** 是否使用了独立的向量端点（用于自检展示） */
    public boolean hasSeparateEmbeddingEndpoint() {
        return StringUtils.hasText(embeddingBaseUrl) || StringUtils.hasText(embeddingApiKey);
    }

    /**
     * Spring AI 会自己拼 /v1/chat/completions，这里去掉用户习惯写的结尾 /v1，
     * 避免出现 /v1/v1/embeddings。
     */
    public String normalizedBaseUrl() {
        return normalize(baseUrl);
    }

    public String normalizedEmbeddingBaseUrl() {
        return normalize(effectiveEmbeddingBaseUrl());
    }

    private String normalize(String raw) {
        String url = raw == null ? "" : raw.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public Chroma getChroma() {
        return chroma;
    }

    public void setChroma(Chroma chroma) {
        this.chroma = chroma;
    }

    public static class Rag {
        private int topK = 4;
        private double similarityThreshold = 0.35;
        private int maxContextChars = 3000;
        private int historyTurns = 6;
        /** 管理端文章同步到向量库时的滑动窗口参数，与入库脚本保持一致 */
        private int chunkSize = 500;
        private int chunkOverlap = 80;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }

        public int getHistoryTurns() {
            return historyTurns;
        }

        public void setHistoryTurns(int historyTurns) {
            this.historyTurns = historyTurns;
        }
    }

    public static class Chroma {
        private String host = "localhost";
        private int port = 8000;
        private String collection = "campus_repair_kb";
        private String tenant = "default_tenant";
        private String database = "default_database";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }
    }
}
