package com.campus.repair.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.dto.UserCreateReq;
import com.campus.repair.module.auth.dto.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final AuthService authService;

    public UserService(UserMapper userMapper, AuthService authService) {
        this.userMapper = userMapper;
        this.authService = authService;
    }

    public PageVO<UserVO> page(Role role, String keyword, Integer status, long page, long pageSize) {
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<SysUser>()
                        .eq(role != null, SysUser::getRole, role)
                        .eq(status != null, SysUser::getStatus, status)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getName, keyword)
                                .or().like(SysUser::getJobNo, keyword))
                        .orderByDesc(SysUser::getId));
        return PageVO.from(p, UserVO::from);
    }

    public UserVO create(UserCreateReq req) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getJobNo, req.jobNo()));
        if (count != null && count > 0) {
            throw BusinessException.param("学工号已存在: " + req.jobNo());
        }
        SysUser u = new SysUser();
        u.setJobNo(req.jobNo());
        u.setName(req.name());
        u.setRole(req.role());
        u.setPhone(req.phone());
        u.setDepartment(req.department());
        u.setAddress(req.address());
        u.setStatus(1);
        u.setPasswordHash(authService.encoder().encode(req.password()));
        userMapper.insert(u);
        return UserVO.from(u);
    }

    public UserVO update(Long id, String name, String phone, String department,
                         String address, Integer status) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (name != null) {
            u.setName(name);
        }
        if (phone != null) {
            u.setPhone(phone);
        }
        if (department != null) {
            u.setDepartment(department);
        }
        if (address != null) {
            u.setAddress(address);
        }
        if (status != null) {
            if (status != 1 && status != 2 && status != 3) {
                throw BusinessException.param("状态只能为 1启用/2停用/3注销");
            }
            u.setStatus(status);
        }
        userMapper.updateById(u);
        return UserVO.from(u);
    }

    public void resetPassword(Long id, String newPassword) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw BusinessException.notFound("用户不存在");
        }
        u.setPasswordHash(authService.encoder().encode(newPassword));
        userMapper.updateById(u);
    }
}
