package com.campus.repair.common;

/**
 * 当前登录用户信息（JWT 载荷）。
 */
public record LoginUser(Long userId, Role role, String name) {
}
