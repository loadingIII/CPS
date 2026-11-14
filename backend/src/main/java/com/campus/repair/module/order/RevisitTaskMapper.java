package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RevisitTaskMapper extends BaseMapper<RevisitTask> {

    @Select("""
            SELECT t.id, t.order_id AS orderId, o.order_no AS orderNo, o.location_text AS locationText,
                   t.worker_id AS workerId, w.name AS workerName, t.rating, t.status,
                   t.remark, t.handled_at AS handledAt, t.created_at AS createdAt,
                   e.content AS evaluationContent
            FROM revisit_task t
            JOIN repair_order o ON o.id = t.order_id
            LEFT JOIN sys_user w ON w.id = t.worker_id
            LEFT JOIN evaluation e ON e.order_id = t.order_id
            WHERE t.status = #{status}
            ORDER BY t.created_at DESC
            LIMIT #{offset}, #{limit}
            """)
    List<com.campus.repair.module.order.dto.RevisitVO> selectPageByStatus(@Param("status") String status,
                                                                          @Param("offset") long offset,
                                                                          @Param("limit") long limit);

    @Select("SELECT COUNT(*) FROM revisit_task WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
