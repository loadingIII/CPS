package com.campus.repair.module.agent.rag;

import java.util.List;

/** 检索结果：context 供模型阅读，refs 供前端展示引用卡片 */
public record RagResult(String context, List<RagRef> refs) {

    public static RagResult empty() {
        return new RagResult("", List.of());
    }

    public boolean isEmpty() {
        return context == null || context.isBlank();
    }

    public String topFaultType() {
        return refs.stream()
                .map(RagRef::faultType)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * @param kbId 管理端知识库文章 id（来自 metadata.kb_id）；md 文件入库的块为 null
     */
    public record RagRef(String id, String title, String faultType, String source,
                         String excerpt, Long kbId, Double score) {
    }
}
