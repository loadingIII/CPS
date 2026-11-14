package com.campus.repair.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.JwtUtil;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.dto.ChangePasswordReq;
import com.campus.repair.module.auth.dto.LoginReq;
import com.campus.repair.module.auth.dto.LoginResp;
import com.campus.repair.module.auth.dto.RegisterReq;
import com.campus.repair.module.auth.dto.UpdateMeReq;
import com.campus.repair.module.auth.dto.UserVO;
import com.campus.repair.module.dict.Campus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
        // 小程序按入口区分报修人/维修工，选错入口直接提示，避免登录后功能全被 403
        if (req.role() != null && u.getRole() != req.role()) {
            String expected = switch (req.role()) {
                case REPORTER -> "报修人";
                case REPAIRER -> "维修工";
                case ADMIN, SYS_ADMIN -> "管理员";
            };
            throw BusinessException.param("该账号不是" + expected + "账号，请切换登录入口");
        }
        String token = jwtUtil.create(u.getId(), u.getRole(), u.getName());
        return new LoginResp(token, UserVO.from(u));
    }

    /**
     * 师生自助注册：角色固定 REPORTER，注册成功直接返回登录态，省掉二次登录。
     * 学工号唯一键兜底并发重复注册。
     */
    @Transactional
    public LoginResp register(RegisterReq req) {
        String jobNo = req.jobNo().trim();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getJobNo, jobNo));
        if (exists != null && exists > 0) {
            throw BusinessException.param("该学工号已注册，请直接登录或联系管理员");
        }

        SysUser u = new SysUser();
        u.setJobNo(jobNo);
        u.setName(req.name().trim());
        u.setPhone(req.phone());
        u.setRole(Role.REPORTER);
        u.setStatus(1);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setCreatedAt(LocalDateTime.now());
        try {
            userMapper.insert(u);
        } catch (DuplicateKeyException e) {
            throw BusinessException.param("该学工号已注册，请直接登录或联系管理员");
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
        if (req.phone() == null && req.department() == null
                && req.address() == null && req.campus() == null) {
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
        if (req.campus() != null) {
            if (u.getRole() != Role.REPORTER) {
                throw BusinessException.param("服务校区由后勤统一维护，不能自行修改");
            }
            if (Campus.of(req.campus()) == null) {
                throw BusinessException.param("所属校区无效");
            }
            u.setCampus(req.campus());
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
