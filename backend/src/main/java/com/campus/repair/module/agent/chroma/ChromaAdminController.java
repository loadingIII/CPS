package com.campus.repair.module.agent.chroma;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 向量库管理接口：文档管理落地前，先用它验证 Chroma 的增删改查。
 */
@Tag(name = "11 向量库管理")
@RestController
@RequestMapping("/api/agent/chroma")
@RequireRole({Role.ADMIN, Role.SYS_ADMIN})
public class ChromaAdminController {

    private final ChromaAdminService service;

    public ChromaAdminController(ChromaAdminService service) {
        this.service = service;
    }

    @Operation(summary = "集合概况（名称 / UUID / 条数）")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.stats());
    }

    @Operation(summary = "分页浏览块（不返回向量）")
    @GetMapping("/chunks")
    public Result<List<ChunkVO>> list(@RequestParam(defaultValue = "20") int limit,
                                      @RequestParam(defaultValue = "0") int offset) {
        return Result.ok(service.list(Math.min(limit, 200), offset));
    }

    @Operation(summary = "批量写入 / 覆盖块（会自动向量化）")
    @PostMapping("/chunks")
    public Result<Map<String, Object>> upsert(@RequestBody List<ChunkReq> chunks) {
        return Result.ok(Map.of("upserted", service.upsert(chunks)));
    }

    @Operation(summary = "按块 id 删除")
    @DeleteMapping("/chunks/{id}")
    public Result<Map<String, Object>> deleteOne(@PathVariable String id) {
        return Result.ok(Map.of("deleted", service.deleteByIds(List.of(id))));
    }

    @Operation(summary = "按文档删除（metadata.doc_id）")
    @DeleteMapping("/docs/{docId}")
    public Result<Map<String, Object>> deleteByDoc(@PathVariable String docId) {
        return Result.ok(Map.of("deleted", service.deleteByDocId(docId)));
    }

    @Operation(summary = "清空所有块（保留集合，无需重启后端）")
    @RequireRole(Role.SYS_ADMIN)
    @PostMapping("/clear")
    public Result<Map<String, Object>> clear() {
        return Result.ok(Map.of("deleted", service.clear()));
    }
}
