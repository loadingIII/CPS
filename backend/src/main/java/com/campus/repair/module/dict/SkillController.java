package com.campus.repair.module.dict;

import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
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

@Tag(name = "04 技能/故障类型字典")
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @Operation(summary = "技能列表（故障类型数据源）")
    @GetMapping
    public Result<List<Skill>> list(@RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.ok(skillService.list(includeDisabled));
    }

    @Operation(summary = "新增技能")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PostMapping
    public Result<Long> create(@RequestBody Map<String, String> body) {
        return Result.ok(skillService.create(body.get("name")).getId());
    }

    @Operation(summary = "修改技能/启停用")
    @RequireRole({Role.ADMIN, Role.SYS_ADMIN})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Boolean enabled = body.get("enabled") == null ? null : (Boolean) body.get("enabled");
        skillService.update(id, name, enabled);
        return Result.ok();
    }
}
