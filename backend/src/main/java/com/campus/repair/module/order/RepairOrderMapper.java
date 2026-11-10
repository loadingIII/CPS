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
    // 派单调度（§5 容量令牌 / §6 抢单池 / 超时扫描）
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

    /** 抢单池：资格（技能/校区/开放时间）下推 SQL，分页与总数同条件 */
    @Select("""
            SELECT o.* FROM repair_order o
            WHERE o.status = 'PENDING' AND o.worker_id IS NULL
              AND o.grab_open_at IS NOT NULL AND o.grab_open_at <= #{now}
              AND (o.fault_type = '其他' OR EXISTS (
                    SELECT 1 FROM worker_skill ws
                    JOIN skill s ON s.id = ws.skill_id
                    WHERE ws.user_id = #{workerId} AND s.name = o.fault_type AND s.enabled = 1))
              AND EXISTS (
                    SELECT 1 FROM worker_scope sc
                    WHERE sc.user_id = #{workerId} AND sc.campus = o.campus)
            ORDER BY o.urgency DESC, o.submitted_at ASC
            LIMIT #{offset}, #{limit}
            """)
    List<RepairOrder> selectGrabPool(@Param("workerId") Long workerId,
                                     @Param("now") LocalDateTime now,
                                     @Param("offset") long offset,
                                     @Param("limit") long limit);

    @Select("""
            SELECT COUNT(*) FROM repair_order o
            WHERE o.status = 'PENDING' AND o.worker_id IS NULL
              AND o.grab_open_at IS NOT NULL AND o.grab_open_at <= #{now}
              AND (o.fault_type = '其他' OR EXISTS (
                    SELECT 1 FROM worker_skill ws
                    JOIN skill s ON s.id = ws.skill_id
                    WHERE ws.user_id = #{workerId} AND s.name = o.fault_type AND s.enabled = 1))
              AND EXISTS (
                    SELECT 1 FROM worker_scope sc
                    WHERE sc.user_id = #{workerId} AND sc.campus = o.campus)
            """)
    long countGrabPool(@Param("workerId") Long workerId, @Param("now") LocalDateTime now);

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
