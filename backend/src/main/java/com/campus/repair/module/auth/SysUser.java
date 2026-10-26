package com.campus.repair.module.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.campus.repair.common.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobNo;

    private String name;

    @JsonIgnore
    private String passwordHash;

    private String phone;

    private Role role;

    private String department;

    private String address;

    /** 1启用 2停用 3注销 */
    private Integer status;

    private LocalDateTime createdAt;
}
