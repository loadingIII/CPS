package com.campus.repair.module.agent;

import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.module.agent.dto.AgentChatResp;
import com.campus.repair.module.agent.rag.KnowledgeBaseTool;
import com.campus.repair.module.agent.rag.RagResult;
import com.campus.repair.module.agent.rag.RagRetriever;
import com.campus.repair.module.kb.KbArticle;
import com.campus.repair.module.kb.KbArticleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能助手编排：Spring AI 工具调用 + Chroma 检索 + 流式输出。
 *
 * <p>降级顺序：Spring AI 不可用（未配密钥 / 未装配 Bean）→ 关键词问答；
 * 模型调用异常 → 同步接口直接回落关键词问答，流式接口把异常抛给上层转成 error 事件。</p>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentProperties props;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final RagRetriever retriever;
    private final KeywordAgentFallback fallback;
    private final KbArticleMapper kbArticleMapper;

    public AgentService(AgentProperties props,
                        ObjectProvider<ChatClient> chatClientProvider,
                        RagRetriever retriever,
                        KeywordAgentFallback fallback,
                        KbArticleMapper kbArticleMapper) {
        this.props = props;
        this.chatClientProvider = chatClientProvider;
        this.retriever = retriever;
        this.fallback = fallback;
        this.kbArticleMapper = kbArticleMapper;
    }

    /** Spring AI 是否真正可用（配置齐全且 Bean 已装配） */
    public boolean aiAvailable() {
        return props.isUsable() && chatClientProvider.getIfAvailable() != null;
    }

    /* ==================== 同步问答（兼容旧接口 / 低版本基础库） ==================== */

    public AgentChatResp chat(String message, String conversationId) {
        String text = normalize(message);
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return fallback.answer(text);
        }

        KnowledgeBaseTool tool = new KnowledgeBaseTool(retriever);
        try {
            String answer = client.prompt()
                    .user(text)
                    .tools(tool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationKey(conversationId)))
                    .call()
                    .content();
            if (!StringUtils.hasText(answer)) {
                return fallback.answer(text);
            }
            return new AgentChatResp(answer.strip(), toRefs(tool.lastResult()), suggestedType(tool.lastResult(), text));
        } catch (Exception e) {
            log.warn("[Agent] 同步问答失败，回落关键词问答：{}", e.getMessage());
            return fallback.answer(text);
        }
    }

    /* ==================== 流式问答 ==================== */

    /**
     * 返回一个流式句柄：content 为增量文本，tool 保存本轮检索结果（结束后取引用）。
     * 降级模式下 preset 非空，content 只发一次完整答案。
     */
    public AgentStream stream(String message, String conversationId) {
        String text = normalize(message);
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            AgentChatResp preset = fallback.answer(text);
            return new AgentStream(Flux.just(preset.answer()), null, preset);
        }

        KnowledgeBaseTool tool = new KnowledgeBaseTool(retriever);
        Flux<String> content = client.prompt()
                .user(text)
                .tools(tool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationKey(conversationId)))
                .stream()
                .content()
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()));
        return new AgentStream(content, tool, null);
    }

    /**
     * 检索结果 → 前端引用卡片。
     * Chroma 里的文档来自 md 文件，不一定对应 MySQL 知识库；能按标题匹配上的补上 id，
     * 前端据此决定点击是否跳转详情。
     */
    public List<AgentChatResp.ArticleRef> toRefs(RagResult result) {
        if (result == null || result.refs().isEmpty()) {
            return List.of();
        }
        // 管理端文章自带 kb_id；md 文件入库的块没有，才按标题回查 MySQL
        List<String> titles = result.refs().stream()
                .filter(r -> r.kbId() == null)
                .map(RagResult.RagRef::title)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, Long> idByTitle = new HashMap<>();
        if (!titles.isEmpty()) {
            try {
                List<KbArticle> matched = kbArticleMapper.selectList(new LambdaQueryWrapper<KbArticle>()
                        .in(KbArticle::getTitle, titles)
                        .eq(KbArticle::getStatus, true));
                for (KbArticle article : matched) {
                    idByTitle.putIfAbsent(article.getTitle(), article.getId());
                }
            } catch (Exception e) {
                log.debug("[Agent] 知识库标题映射失败：{}", e.getMessage());
            }
        }
        return result.refs().stream()
                .map(r -> new AgentChatResp.ArticleRef(
                        r.kbId() != null ? r.kbId() : idByTitle.get(r.title()),
                        r.title(), r.faultType(),
                        StringUtils.hasText(r.excerpt()) ? r.excerpt() : r.source()))
                .collect(Collectors.toList());
    }

    public String suggestedType(RagResult result, String message) {
        if (result != null && result.topFaultType() != null) {
            return result.topFaultType();
        }
        return fallback.classify(message);
    }

    /* ==================== 内部工具 ==================== */

    private String normalize(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw BusinessException.param("请描述一下故障现象");
        }
        return text;
    }

    /** 会话记忆按「用户 + 前端会话 id」隔离，避免多人串话 */
    private String conversationKey(String conversationId) {
        Long uid;
        try {
            uid = AuthContext.userId();
        } catch (Exception e) {
            uid = null;
        }
        String cid = StringUtils.hasText(conversationId) ? conversationId.trim() : "default";
        return (uid == null ? "anon" : "u" + uid) + ":" + cid;
    }

    public AgentProperties properties() {
        return props;
    }

    public long fallbackArticleCount() {
        return fallback.publishedCount();
    }

    /** 流式句柄：preset 非空表示已降级为一次性答案 */
    public record AgentStream(Flux<String> content, KnowledgeBaseTool tool, AgentChatResp preset) {

        public boolean degraded() {
            return preset != null;
        }
    }
}
