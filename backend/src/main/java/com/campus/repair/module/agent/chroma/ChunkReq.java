package com.campus.repair.module.agent.chroma;

import java.util.Map;

/**
 * 向量块写入请求。
 *
 * @param id       块唯一 id，约定 {@code docId:序号}，重复写入即覆盖
 * @param text     原文（会被向量化）
 * @param metadata 元数据，建议包含 doc_id / title / fault_type / source
 */
public record ChunkReq(String id, String text, Map<String, Object> metadata) {
}
