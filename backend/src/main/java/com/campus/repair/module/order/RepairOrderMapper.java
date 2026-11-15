package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 当日工单号最大序号（工单号规则 §2.3：BX+yyyyMMdd+4位序号） */
    @Select("SELECT IFNULL(MAX(CAST(RIGHT(order_no, 4) AS UNSIGNED)), 0) "
            + "FROM repair_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    int selectMaxSeqToday(@Param("prefix") String prefix);

    @Select("SELECT status AS status, COUNT(*) AS total FROM repair_order GROUP BY status")
    List<Map<String, Object>> countByStatus();

    /** 真正待派单：待处理且未指派，口径与派单台 POOL 队列一致 */
    @Select("SELECT COUNT(*) FROM repair_order WHERE status = 'PENDING' AND worker_id IS NULL")
    long countPendingPool();

    @Select("""
            SELECT DATE(submitted_at) AS day, COUNT(*) AS total
            FROM repair_order
            WHERE submitted_at >= #{from} AND submitted_at < #{to}
            GROUP BY DATE(submitted_at)
            ORDER BY day ASC
            """)
    List<Map<String, Object>> countBySubmittedDay(@Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    @Select("""
            SELECT fault_type AS faultType, COUNT(*) AS total
            FROM repair_order
            WHERE submitted_at >= #{from} AND submitted_at < #{to}
            GROUP BY fault_type
            ORDER BY total DESC, fault_type ASC
            """)
    List<Map<String, Object>> countByFaultType(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    // ------------------------------------------------------------------
    // 派单调度（§5 容量令牌 / 超时扫描）
    // ------------------------------------------------------------------

    /** 在办负载：PENDING（已派待接）+ PROCESSING，可排除指定工单（改派场景） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM repair_order
            WHERE worker_id = #{workerId} AND status IN ('PENDING', 'PROCESSING')
            <if test="excludeOrderId != null"> AND id != #{excludeOrderId}</if>
            </script>
            """)
    long countActiveByWorker(@Param("workerId") Long workerId,
                             @Param("excludeOrderId") Long excludeOrderId);

    /** 接单超时：已派待接且超过 assign_expires_at */
    @Select("""
            SELECT * FROM repair_order
            WHERE status = 'PENDING' AND worker_id IS NOT NULL
              AND assign_expires_at IS NOT NULL AND assign_expires_at < #{now}
            ORDER BY assign_expires_at ASC
            LIMIT #{limit}
            """)
    List<RepairOrder> selectExpiredOffers(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    /** 处理超时：处理中且超过 due_at，尚未标记 */
    @Select("""
            SELECT * FROM repair_order
            WHERE status = 'PROCESSING' AND is_overdue = 0
              AND due_at IS NOT NULL AND due_at < #{now}
            ORDER BY due_at ASC
            LIMIT #{limit}
            """)
    List<RepairOrder> selectOverdueProcessing(@Param("now") LocalDateTime now,
                                              @Param("limit") int limit);

    /** 完工超过指定时间仍未评价 */
    @Select("""
            SELECT * FROM repair_order
            WHERE status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at < #{before}
            ORDER BY completed_at ASC
            LIMIT #{limit}
            """)
    List<RepairOrder> selectCompletedBefore(@Param("before") LocalDateTime before,
                                            @Param("limit") int limit);

    /** 按完工日统计完工量与按时量（R2 按时完成率趋势，分母按 completed_at） */
    @Select("""
            SELECT DATE(completed_at) AS day, COUNT(*) AS done,
                   SUM(CASE WHEN due_at IS NOT NULL AND completed_at <= due_at THEN 1 ELSE 0 END) AS onTime
            FROM repair_order
            WHERE status IN ('COMPLETED', 'EVALUATED')
              AND completed_at >= #{from} AND completed_at < #{to}
            GROUP BY DATE(completed_at)
            ORDER BY day ASC
            """)
    List<Map<String, Object>> countOntimeByDay(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /** 维修工绩效（R2-F9-3）：完成量 / 按时量 / 评价均分 / 综合评分 */
    @Select("""
            SELECT o.worker_id AS workerId, u.name AS name, COUNT(*) AS done,
                   SUM(CASE WHEN o.due_at IS NOT NULL AND o.completed_at <= o.due_at THEN 1 ELSE 0 END) AS onTime,
                   ROUND(AVG(e.rating), 2) AS avgRating, MAX(p.score) AS score
            FROM repair_order o
            JOIN sys_user u ON u.id = o.worker_id
            LEFT JOIN evaluation e ON e.order_id = o.id
            LEFT JOIN worker_profile p ON p.user_id = o.worker_id
            WHERE o.status IN ('COMPLETED', 'EVALUATED') AND o.completed_at >= #{from}
            GROUP BY o.worker_id, u.name
            ORDER BY done DESC
            """)
    List<Map<String, Object>> selectWorkerPerformance(@Param("from") LocalDateTime from);

    /** 近 N 天按时完成统计（派单评分 Timeliness 因子） */
    @Select("""
            <script>
            SELECT worker_id AS workerId, COUNT(*) AS done,
                   SUM(CASE WHEN due_at IS NOT NULL AND completed_at &lt;= due_at THEN 1 ELSE 0 END) AS onTime
            FROM repair_order
            WHERE status IN ('COMPLETED', 'EVALUATED')
              AND completed_at &gt;= #{from} AND worker_id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY worker_id
            </script>
            """)
    List<Map<String, Object>> countTimeliness(@Param("ids") List<Long> ids,
                                              @Param("from") LocalDateTime from);
}
