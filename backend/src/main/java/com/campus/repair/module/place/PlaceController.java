package com.campus.repair.module.place;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.place.dto.PlaceNodeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "03 位置管理")
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @Operation(summary = "位置树（四级：校区/片区/楼栋/点位）")
    @GetMapping("/tree")
    public Result<List<PlaceNodeVO>> tree(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.ok(placeService.tree(includeDisabled));
    }

    @Operation(summary = "新增位置节点")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, Object> body) {
        PlaceType type = PlaceType.valueOf((String) body.get("type"));
        Long parentId = body.get("parentId") == null ? null : ((Number) body.get("parentId")).longValue();
        String name = (String) body.get("name");
        String code = (String) body.get("code");
        if (name == null || name.isBlank()) {
            return Result.fail(com.campus.repair.common.ErrorCode.PARAM_ERROR, "名称不能为空");
        }
        return Result.ok(placeService.create(type, parentId, name, code).getId());
    }

    @Operation(summary = "修改位置（名称/编码/启用）")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String code = (String) body.get("code");
        Boolean enabled = body.get("enabled") == null ? null : (Boolean) body.get("enabled");
        placeService.update(id, name, code, enabled);
        return Result.ok();
    }
}
