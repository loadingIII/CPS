package com.campus.repair.module.agent.config;

import com.campus.repair.module.agent.AgentProperties;
import io.netty.channel.ChannelOption;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.time.Duration;

/**
 * Spring AI 手工装配。
 *
 * <p>刻意不引 starter：① 避免 autoconfigure 与 Spring Boot 小版本的强绑定；
 * ② 没有配置密钥时不会因为 api-key 为空导致整个应用启动失败。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "agent", name = "enabled", havingValue = "true")
@ConditionalOnExpression("'${agent.api-key:}'.length() > 0 && '${agent.base-url:}'.length() > 0")
public class AgentAiConfig {

    /**
     * 模型调用统一超时：连接 5s、读 timeout-seconds；
     * 重试降到 2 次，避免密钥/网关异常时一次请求挂几分钟。
     */
    @Bean
    public RetryTemplate aiRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(2)
                .fixedBackoff(400)
                .retryOn(IOException.class)
                .build();
    }

    @Bean("chatOpenAiApi")
    public OpenAiApi chatOpenAiApi(AgentProperties props,
                                   RestClient.Builder restClientBuilder,
                                   WebClient.Builder webClientBuilder) {
        return buildApi(props.normalizedBaseUrl(), props.getApiKey(), props,
                restClientBuilder, webClientBuilder);
    }

    /**
     * 向量模型可以单独一套端点/密钥（例如对话走自建网关、embedding 走阿里云百炼）。
     * 未单独配置时回落到对话配置，保持向后兼容。
     */
    @Bean("embeddingOpenAiApi")
    public OpenAiApi embeddingOpenAiApi(AgentProperties props,
                                        RestClient.Builder restClientBuilder,
                                        WebClient.Builder webClientBuilder) {
        return buildApi(props.normalizedEmbeddingBaseUrl(), props.effectiveEmbeddingApiKey(), props,
                restClientBuilder, webClientBuilder);
    }

    private OpenAiApi buildApi(String baseUrl, String apiKey, AgentProperties props,
                               RestClient.Builder restClientBuilder,
                               WebClient.Builder webClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(props.getTimeoutSeconds()));

        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder.requestFactory(requestFactory))
                .webClientBuilder(webClientBuilder.clientConnector(
                        new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient)))
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(@Qualifier("chatOpenAiApi") OpenAiApi chatOpenAiApi,
                                           AgentProperties props,
                                           @Qualifier("aiRetryTemplate") RetryTemplate retryTemplate) {
        return OpenAiChatModel.builder()
                .openAiApi(chatOpenAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(props.getChatModel())
                        .temperature(props.getTemperature())
                        .build())
                .retryTemplate(retryTemplate)
                .build();
    }

    /**
     * 不向模型传 dimensions：不同 OpenAI 兼容端点对该参数支持不一，
     * AI_EMBEDDING_DIMENSIONS 只用于入库脚本校验与日志提示。
     */
    @Bean
    public EmbeddingModel embeddingModel(@Qualifier("embeddingOpenAiApi") OpenAiApi embeddingOpenAiApi,
                                         AgentProperties props,
                                         @Qualifier("aiRetryTemplate") RetryTemplate retryTemplate) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(props.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(embeddingOpenAiApi, MetadataMode.EMBED, options, retryTemplate);
    }

    @Bean
    public ChromaApi chromaApi(AgentProperties props) {
        return ChromaApi.builder().baseUrl(props.chromaBaseUrl()).build();
    }

    @Bean
    @Lazy
    public VectorStore vectorStore(ChromaApi chromaApi, EmbeddingModel embeddingModel, AgentProperties props) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName(props.getChroma().getCollection())
                .tenantName(props.getChroma().getTenant())
                .databaseName(props.getChroma().getDatabase())
                // 集合由入库脚本创建，应用只读；不存在时由检索层给出可读错误
                .initializeSchema(false)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(AgentProperties props) {
        int maxMessages = Math.max(4, props.getRag().getHistoryTurns() * 2);
        return MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(AgentPrompts.SYSTEM)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
