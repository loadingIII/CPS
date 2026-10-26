package com.campus.repair.common;

/**
 * 用户角色（对应 sys_user.role 与 order_log.operator_role 中的用户角色部分）。
 */
public enum Role {
    /** 报修人（师生） */
    REPORTER,
    /** 后勤管理员 */
    ADMIN,
    /** 维修工 */
    REPAIRER,
    /** 系统管理员 */
    SYS_ADMIN
}
