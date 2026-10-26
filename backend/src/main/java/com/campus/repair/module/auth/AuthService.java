package com.campus.repair.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.JwtUtil;
import com.campus.repair.module.auth.dto.ChangePasswordReq;
import com.campus.repair.module.auth.dto.LoginReq;
import com.campus.repair.module.auth.dto.LoginResp;
import com.campus.repair.module.auth.dto.UpdateMeReq;
import com.campus.repair.module.auth.dto.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public LoginResp login(LoginReq req) {
        SysUser u = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getJobNo, req.jobNo()));
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw BusinessException.param("账号或密码错误");
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw BusinessException.param("账号已停用或注销，请联系管理员");
        }
        String token = jwtUtil.create(u.getId(), u.getRole(), u.getName());
        return new LoginResp(token, UserVO.from(u));
    }

    public UserVO me() {
        SysUser u = userMapper.selectById(AuthContext.userId());
        if (u == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return UserVO.from(u);
    }

    public void updateMe(UpdateMeReq req) {
        if (req.phone() == null && req.department() == null && req.address() == null) {
            throw BusinessException.param("至少填写一项要修改的信息");
        }
        SysUser u = userMapper.selectById(AuthContext.userId());
        if (u == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (req.phone() != null) {
            u.setPhone(req.phone());
        }
        if (req.department() != null) {
            u.setDepartment(req.department());
        }
        if (req.address() != null) {
            u.setAddress(req.address());
        }
        userMapper.updateById(u);
    }

    public void changePassword(ChangePasswordReq req) {
        SysUser u = userMapper.selectById(AuthContext.userId());
        if (u == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!encoder.matches(req.oldPassword(), u.getPasswordHash())) {
            throw BusinessException.param("旧密码错误");
        }
        u.setPasswordHash(encoder.encode(req.newPassword()));
        userMapper.updateById(u);
    }

    public BCryptPasswordEncoder encoder() {
        return encoder;
    }
}
