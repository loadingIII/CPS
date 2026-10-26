package com.campus.repair.module.auth;

import com.campus.repair.common.PageVO;
import com.campus.repair.common.RequireRole;
import com.campus.repair.common.Result;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.dto.ResetPasswordReq;
import com.campus.repair.module.auth.dto.UserCreateReq;
import com.campus.repair.module.auth.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "02 账号管理（系统管理员）")
@RestController
@RequestMapping("/api/users")
@RequireRole(Role.SYS_ADMIN)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "用户分页列表")
    @GetMapping
    public Result<PageVO<UserVO>> page(@RequestParam(required = false) Role role,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(userService.page(role, keyword, status, page, pageSize));
    }

    @Operation(summary = "创建账号")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateReq req) {
        return Result.ok(userService.create(req));
    }

    @Operation(summary = "修改用户信息/启停用")
    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id,
                                 @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String phone = (String) body.get("phone");
        String department = (String) body.get("department");
        String address = (String) body.get("address");
        Integer status = body.get("status") == null ? null : ((Number) body.get("status")).intValue();
        return Result.ok(userService.update(id, name, phone, department, address, status));
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordReq req) {
        userService.resetPassword(id, req.newPassword());
        return Result.ok();
    }
}
