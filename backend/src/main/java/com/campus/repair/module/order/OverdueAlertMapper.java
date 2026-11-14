package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OverdueAlertMapper extends BaseMapper<OverdueAlert> {

    @Select("""
            SELECT a.id, a.order_id AS orderId, o.order_no AS orderNo, o.location_text AS locationText,
                   o.fault_type AS faultType, o.urgency AS urgency, o.status AS status,
                   a.stage, a.level, a.handled, a.created_at AS createdAt
            FROM overdue_alert a
            JOIN repair_order o ON o.id = a.order_id
            WHERE a.handled = #{handled}
            ORDER BY a.created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<com.campus.repair.module.order.dto.AlertVO> selectPageByHandled(@Param("handled") boolean handled,
                                                                         @Param("offset") long offset,
                                                                         @Param("limit") long limit);

    @Select("SELECT COUNT(*) FROM overdue_alert WHERE handled = #{handled}")
    long countByHandled(@Param("handled") boolean handled);

    /** 同一工单已有预警数，用于计算升级级别 */
    @Select("SELECT COUNT(*) FROM overdue_alert WHERE order_id = #{orderId}")
    int countByOrder(@Param("orderId") Long orderId);
}
