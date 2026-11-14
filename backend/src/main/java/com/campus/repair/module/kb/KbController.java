package com.campus.repair.module.kb;

import com.campus.repair.common.AuthContext;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "11 维修知识库")
@RestController
@RequestMapping("/api/kb/articles")
public class KbController {

    private final KbArticleService kbArticleService;

    public KbController(KbArticleService kbArticleService) {
        this.kbArticleService = kbArticleService;
    }

    @Operation(summary = "知识库列表（分类 + 关键词）")
    @GetMapping
    public Result<PageVO<KbArticle>> list(@RequestParam(required = false) String faultType,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(kbArticleService.pagePublished(faultType, keyword, page, pageSize));
    }

    @Operation(summary = "知识库详情（浏览量 +1）")
    @GetMapping("/{id}")
    public Result<KbArticle> detail(@PathVariable Long id) {
        boolean admin = AuthContext.role() == Role.ADMIN || AuthContext.role() == Role.SYS_ADMIN;
        return Result.ok(kbArticleService.detail(id, admin));
    }

    @Operation(summary = "管理端知识库列表（含停用）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @GetMapping("/manage")
    public Result<PageVO<KbArticle>> manage(@RequestParam(required = false) String faultType,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(kbArticleService.pageAdmin(faultType, keyword, page, pageSize));
    }

    @Operation(summary = "新增文章")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping
    public Result<Long> create(@Valid @RequestBody KbReq req) {
        return Result.ok(kbArticleService.create(req));
    }

    @Operation(summary = "修改文章")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody KbReq req) {
        kbArticleService.update(id, req);
        return Result.ok();
    }

    @Operation(summary = "上传 Markdown 并直接入库（切分 + 向量化）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KbImportResult> importMarkdown(@RequestPart("file") MultipartFile file) {
        return Result.ok(kbArticleService.importMarkdown(file));
    }

    @Operation(summary = "删除文章（同时清理向量块）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbArticleService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "重建向量索引（把启用中的文章重新切片入库）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping("/reindex")
    public Result<KbVectorIndexService.SyncReport> reindex() {
        return Result.ok(kbArticleService.reindex());
    }
}
