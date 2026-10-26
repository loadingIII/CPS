package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderLogMapper extends BaseMapper<OrderLog> {

    @Select("""
            SELECT l.action, l.operator_role AS operatorRole, l.remark,
                   l.created_at AS createdAt, u.name AS operatorName
            FROM order_log l
            LEFT JOIN sys_user u ON l.operator_id = u.id
            WHERE l.order_id = #{orderId}
            ORDER BY l.id ASC
            """)
    List<OrderLogVO> selectByOrderId(@Param("orderId") Long orderId);
}
