package com.campus.repair.module.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.AuthContext;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.PageVO;
import com.campus.repair.common.Role;
import com.campus.repair.module.auth.SysUser;
import com.campus.repair.module.auth.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务（R2）。
 *
 * <p>约定：通知写入不阻塞主流程——业务方法已在同一事务内，通知失败只记日志不抛出；
 * 通知只做站内消息，不接微信订阅消息（R3）。
 */
@Service
public class NotificationService {

    public static final String AUDIT_PASS = "AUDIT_PASS";
    public static final String AUDIT_REJECT = "AUDIT_REJECT";
    public static final String ASSIGN = "ASSIGN";
    public static final String ACCEPT = "ACCEPT";
    public static final String REJECT = "REJECT";
    public static final String FINISH = "FINISH";
    public static final String RATE = "RATE";
    public static final String URGE = "URGE";
    public static final String OVERDUE = "OVERDUE";
    public static final String REVISIT = "REVISIT";
    public static final String MERGE = "MERGE";
    public static final String CANCEL = "CANCEL";
    public static final String REASSIGN = "REASSIGN";

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    /** 给单个用户发通知 */
    public void notifyUser(Long receiverId, String type, String content, Long bizId) {
        if (receiverId == null) {
            return;
        }
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setType(type);
        n.setContent(trim(content));
        n.setBizId(bizId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    /** 给全部启用中的管理员发通知（拒单、转单、超时预警等需要人工介入的事件） */
    public void notifyAdmins(String type, String content, Long bizId) {
        List<SysUser> admins = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, Role.ADMIN, Role.SYS_ADMIN)
                .eq(SysUser::getStatus, 1));
        for (SysUser admin : admins) {
            notifyUser(admin.getId(), type, content, bizId);
        }
    }

    public PageVO<Notification> page(boolean unreadOnly, long page, long pageSize) {
        Long uid = AuthContext.userId();
        Page<Notification> p = notificationMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getReceiverId, uid)
                        .eq(unreadOnly, Notification::getIsRead, false)
                        .orderByDesc(Notification::getCreatedAt)
                        .orderByDesc(Notification::getId));
        return new PageVO<>(p.getRecords(), p.getTotal());
    }

    public long unreadCount() {
        Long uid = AuthContext.userId();
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, uid)
                .eq(Notification::getIsRead, false));
        return count == null ? 0 : count;
    }

    public void markRead(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !AuthContext.userId().equals(n.getReceiverId())) {
            throw BusinessException.notFound("通知不存在");
        }
        if (Boolean.TRUE.equals(n.getIsRead())) {
            return;
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .set(Notification::getIsRead, true));
    }

    public void markAllRead() {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, AuthContext.userId())
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
    }

    private String trim(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 480 ? content.substring(0, 477) + "..." : content;
    }
}
