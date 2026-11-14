package com.campus.repair.module.agent.rag;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 唯一暴露的工具：知识库语义检索。
 *
 * <p>每次问答创建一个实例，检索结果留在 {@link #lastResult()}，
 * 供 SSE 结束时回传引用卡片与建议故障类型，避免线程间共享状态。</p>
 */
public class KnowledgeBaseTool {

    private final RagRetriever retriever;
    private final AtomicReference<RagResult> last = new AtomicReference<>(RagResult.empty());

    public KnowledgeBaseTool(RagRetriever retriever) {
        this.retriever = retriever;
    }

    @Tool(name = "searchKnowledgeBase",
            description = "检索校园报修知识库，返回与故障现象最相关的处理步骤。"
                    + "任何故障类问题都必须先调用本工具，再依据返回内容回答。")
    public String searchKnowledgeBase(
            @ToolParam(description = "故障描述或检索关键词，例如：空调不制冷、宿舍跳闸、门锁打不开")
            String query) {
        RagResult result = retriever.retrieve(query);
        last.set(result);
        if (result.isEmpty()) {
            return "知识库未检索到相关内容。请如实告知用户没有找到教程，并建议提交报修。";
        }
        return result.context();
    }

    public RagResult lastResult() {
        return last.get();
    }
}
