package com.campus.repair.module.auth;

import com.campus.repair.common.PublicApi;
import com.campus.repair.common.Result;
import com.campus.repair.module.auth.dto.ChangePasswordReq;
import com.campus.repair.module.auth.dto.LoginReq;
import com.campus.repair.module.auth.dto.LoginResp;
import com.campus.repair.module.auth.dto.RegisterReq;
import com.campus.repair.module.auth.dto.UpdateMeReq;
import com.campus.repair.module.auth.dto.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01 认证与个人中心")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicApi
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PublicApi
    @Operation(summary = "师生自助注册（仅报修人角色）")
    @PostMapping("/register")
    public Result<LoginResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me());
    }

    @Operation(summary = "修改本人资料（手机号/部门/住址）")
    @PutMapping("/me")
    public Result<Void> updateMe(@RequestBody UpdateMeReq req) {
        authService.updateMe(req);
        return Result.ok();
    }

    @Operation(summary = "修改本人密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return Result.ok();
    }
}
