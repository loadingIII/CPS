package com.campus.repair.module.agent.chroma;

import java.util.Map;

/** 向量块视图：不含向量本身，避免列表接口背大字段 */
public record ChunkVO(String id, String text, Map<String, String> metadata) {
}
