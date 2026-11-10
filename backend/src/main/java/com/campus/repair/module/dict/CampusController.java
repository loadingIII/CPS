package com.campus.repair.module.dict;

import com.campus.repair.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "03 校区字典（只读）")
@RestController
@RequestMapping("/api/campuses")
public class CampusController {

    @Operation(summary = "校区列表（报修必选 / 维修工负责范围）")
    @GetMapping
    public Result<List<CampusVO>> list() {
        return Result.ok(Arrays.stream(Campus.values())
                .map(c -> new CampusVO(c.name(), c.label(), c.address(), c.lng(), c.lat()))
                .toList());
    }
}
