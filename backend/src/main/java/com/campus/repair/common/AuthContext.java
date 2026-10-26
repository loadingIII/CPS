package com.campus.repair.common;

/**
 * 当前登录用户上下文（JWT 拦截器注入，请求结束清理）。
 */
public class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return u.userId();
    }

    public static Role role() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return u.role();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
