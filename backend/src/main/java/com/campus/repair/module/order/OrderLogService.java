package com.campus.repair.module.order;

import com.campus.repair.common.Role;
import com.campus.repair.module.order.statemachine.OrderAction;
import org.springframework.stereotype.Service;

/**
 * 状态时间线写入：任何状态变更同事务调用（关键操作留痕）。
 */
@Service
public class OrderLogService {

    private final OrderLogMapper logMapper;

    public OrderLogService(OrderLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    public void log(Long orderId, OrderAction action, Long operatorId, Role operatorRole, String remark) {
        OrderLog l = new OrderLog();
        l.setOrderId(orderId);
        l.setAction(action.label());
        l.setOperatorId(operatorId);
        l.setOperatorRole(operatorRole == null ? "SYSTEM" : operatorRole.name());
        l.setRemark(remark);
        logMapper.insert(l);
    }
}
