package com.campus.repair.module.auth.dto;

import com.campus.repair.common.Role;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.dict.Campus;

/**
 * 用户信息视图对象（不含密码）。
 */
public record UserVO(Long id, String jobNo, String name, Role role, String phone,
                     String department, String address, Integer status,
                     String campus, String campusName) {

    public static UserVO from(SysUser u) {
        return new UserVO(u.getId(), u.getJobNo(), u.getName(), u.getRole(),
                u.getPhone(), u.getDepartment(), u.getAddress(), u.getStatus(),
                u.getCampus(),
                u.getCampus() == null ? null : Campus.labelOf(u.getCampus()));
    }
}
