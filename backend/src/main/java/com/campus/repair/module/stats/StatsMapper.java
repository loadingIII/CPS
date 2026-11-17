package com.campus.repair.module.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * R3 统计查询：校区维度、异常看板、维修工画像、AI 采纳率、导出。
 *
 * <p>只读查询，不写业务表；分母口径在 Service 层统一换算。</p>
 */
@Mapper
public interface StatsMapper {

    /* ==================== 校区维度 ==================== */

    @Select("""
            SELECT campus AS campus, COUNT(*) AS total,
                   SUM(CASE WHEN urgency = 'URGENT' THEN 1 ELSE 0 END) AS urgent
            FROM repair_order
            WHERE submitted_at >= #{from} AND submitted_at < #{to}
            GROUP BY campus
            """)
    List<Map<String, Object>> campusSubmitted(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Select("""
            SELECT campus AS campus, COUNT(*) AS completed,
                   SUM(CASE WHEN due_at IS NOT NULL AND completed_at <= due_at THEN 1 ELSE 0 END) AS onTime,
                   AVG(CASE WHEN accepted_at IS NOT NULL
                            THEN TIMESTAMPDIFF(MINUTE, accepted_at, completed_at) END) AS avgMinutes
            FROM repair_order
            WHERE status IN ('COMPLETED', 'EVALUATED')
              AND completed_at >= #{from} AND completed_at < #{to}
            GROUP BY campus
            """)
    List<Map<String, Object>> campusCompleted(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    @Select("""
            SELECT campus AS campus, fault_type AS faultType, COUNT(*) AS total
            FROM repair_order
            WHERE submitted_at >= #{from} AND submitted_at < #{to}
            GROUP BY campus, fault_type
            ORDER BY campus ASC, total DESC, fault_type ASC
            """)
    List<Map<String, Object>> campusFaultTypes(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /* ==================== 异常看板 ==================== */

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, location_text AS locationText,
                   status, worker_id AS workerId, submitted_at AS baseTime,
                   dispatch_count AS dispatchCount, reject_count AS rejectCount
            FROM repair_order
            WHERE status = 'PENDING_AUDIT' AND submitted_at < #{before}
            ORDER BY submitted_at ASC
            LIMIT 200
            """)
    List<Map<String, Object>> longPendingAudit(@Param("before") LocalDateTime before);

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, location_text AS locationText,
                   status, worker_id AS workerId, IFNULL(audited_at, submitted_at) AS baseTime,
                   dispatch_count AS dispatchCount, reject_count AS rejectCount
            FROM repair_order
            WHERE status = 'PENDING' AND worker_id IS NULL
              AND IFNULL(audited_at, submitted_at) < #{before}
            ORDER BY IFNULL(audited_at, submitted_at) ASC
            LIMIT 200
            """)
    List<Map<String, Object>> longPendingPool(@Param("before") LocalDateTime before);

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, location_text AS locationText,
                   status, worker_id AS workerId, assign_expires_at AS baseTime,
                   dispatch_count AS dispatchCount, reject_count AS rejectCount
            FROM repair_order
            WHERE status = 'PENDING' AND worker_id IS NOT NULL
              AND assign_expires_at IS NOT NULL AND assign_expires_at < #{now}
            ORDER BY assign_expires_at ASC
            LIMIT 200
            """)
    List<Map<String, Object>> offerExpired(@Param("now") LocalDateTime now);

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, location_text AS locationText,
                   status, worker_id AS workerId, due_at AS baseTime,
                   dispatch_count AS dispatchCount, reject_count AS rejectCount
            FROM repair_order
            WHERE status = 'PROCESSING'
              AND due_at IS NOT NULL AND due_at < #{now}
            ORDER BY due_at ASC
            LIMIT 200
            """)
    List<Map<String, Object>> processOverdue(@Param("now") LocalDateTime now);

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, location_text AS locationText,
                   status, worker_id AS workerId, submitted_at AS baseTime,
                   dispatch_count AS dispatchCount, reject_count AS rejectCount
            FROM repair_order
            WHERE status IN ('PENDING', 'PROCESSING') AND dispatch_count >= 3
            ORDER BY dispatch_count DESC, submitted_at ASC
            LIMIT 200
            """)
    List<Map<String, Object>> multiDispatch();

    @Select("""
            SELECT o.id AS orderId, o.order_no AS orderNo, o.campus, o.location_text AS locationText,
                   o.status, o.worker_id AS workerId, o.submitted_at AS baseTime,
                   o.dispatch_count AS dispatchCount, o.reject_count AS rejectCount
            FROM repair_order o
            WHERE o.status IN ('PENDING', 'PROCESSING')
              AND (o.reject_count >= 2
                   OR (SELECT COUNT(*) FROM overdue_alert a WHERE a.order_id = o.id) >= 2)
            ORDER BY o.reject_count DESC, o.submitted_at ASC
            LIMIT 200
            """)
    List<Map<String, Object>> multiTimeout();

    /** 返修识别：窗口前后各留 7 天，避免漏掉跨窗口的“完工后再次报修” */
    @Select("""
            SELECT id AS orderId, order_no AS orderNo, campus, fault_type AS faultType,
                   location_text AS locationText, status, worker_id AS workerId,
                   submitted_at AS submittedAt, completed_at AS completedAt
            FROM repair_order
            WHERE status NOT IN ('CANCELLED', 'REJECTED')
              AND (completed_at >= #{from} OR submitted_at >= #{from})
            ORDER BY campus ASC, fault_type ASC, location_text ASC, submitted_at ASC
            """)
    List<Map<String, Object>> ordersForRepair(@Param("from") LocalDateTime from);

    /* ==================== 维修工画像 ==================== */

    @Select("""
            SELECT COUNT(*) AS done,
                   SUM(CASE WHEN o.due_at IS NOT NULL AND o.completed_at <= o.due_at THEN 1 ELSE 0 END) AS onTime,
                   AVG(e.rating) AS avgRating,
                   AVG(CASE WHEN o.accepted_at IS NOT NULL
                            THEN TIMESTAMPDIFF(MINUTE, o.accepted_at, o.completed_at) END) AS avgMinutes
            FROM repair_order o
            LEFT JOIN evaluation e ON e.order_id = o.id
            WHERE o.worker_id = #{workerId} AND o.status IN ('COMPLETED', 'EVALUATED')
              AND o.completed_at >= #{from} AND o.completed_at < #{to}
            """)
    Map<String, Object> workerStats(@Param("workerId") Long workerId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    @Select("""
            SELECT o.order_no AS orderNo, o.fault_type AS faultType,
                   o.location_text AS locationText, o.completed_at AS completedAt,
                   o.is_overdue AS isOverdue, e.rating AS rating
            FROM repair_order o
            LEFT JOIN evaluation e ON e.order_id = o.id
            WHERE o.worker_id = #{workerId} AND o.status IN ('COMPLETED', 'EVALUATED')
              AND o.completed_at IS NOT NULL
            ORDER BY o.completed_at DESC
            LIMIT 10
            """)
    List<Map<String, Object>> recentCompletedByWorker(@Param("workerId") Long workerId);

    @Select("""
            SELECT COUNT(*) FROM order_log
            WHERE operator_id = #{workerId} AND action = '处理超时回收' AND created_at >= #{from}
            """)
    long countOverdueRecycles(@Param("workerId") Long workerId,
                              @Param("from") LocalDateTime from);

    /* ==================== AI 建议采纳率 ==================== */

    @Select("""
            SELECT COUNT(*) AS suggested,
                   SUM(CASE WHEN ai_suggestion_used = 1 THEN 1 ELSE 0 END) AS used
            FROM repair_order
            WHERE ai_suggested_type IS NOT NULL
              AND submitted_at >= #{from} AND submitted_at < #{to}
            """)
    Map<String, Object> aiSuggestionSummary(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Select("""
            SELECT ai_suggested_type AS type, COUNT(*) AS suggested,
                   SUM(CASE WHEN ai_suggestion_used = 1 THEN 1 ELSE 0 END) AS used
            FROM repair_order
            WHERE ai_suggested_type IS NOT NULL
              AND submitted_at >= #{from} AND submitted_at < #{to}
            GROUP BY ai_suggested_type
            ORDER BY suggested DESC, type ASC
            """)
    List<Map<String, Object>> aiSuggestionByType(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to);

    /* ==================== 导出 ==================== */

    @Select("""
            <script>
            SELECT o.order_no AS orderNo, o.campus AS campus, o.location_text AS locationText,
                   o.fault_type AS faultType, o.urgency AS urgency, o.status AS status,
                   r.name AS reporterName, w.name AS workerName,
                   o.submitted_at AS submittedAt, o.audited_at AS auditedAt,
                   o.accepted_at AS acceptedAt, o.completed_at AS completedAt,
                   o.is_overdue AS isOverdue, e.rating AS rating
            FROM repair_order o
            JOIN sys_user r ON r.id = o.reporter_id
            LEFT JOIN sys_user w ON w.id = o.worker_id
            LEFT JOIN evaluation e ON e.order_id = o.id
            WHERE 1 = 1
            <if test="status != null"> AND o.status = #{status}</if>
            <if test="urgency != null"> AND o.urgency = #{urgency}</if>
            <if test="faultType != null and faultType != ''"> AND o.fault_type = #{faultType}</if>
            <if test="campus != null and campus != ''"> AND o.campus = #{campus}</if>
            <if test="keyword != null and keyword != ''">
              AND (o.order_no LIKE CONCAT('%', #{keyword}, '%')
                   OR o.location_text LIKE CONCAT('%', #{keyword}, '%')
                   OR r.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="from != null"> AND o.submitted_at &gt;= #{from}</if>
            <if test="to != null"> AND o.submitted_at &lt; #{to}</if>
            ORDER BY o.submitted_at DESC
            LIMIT 5001
            </script>
            """)
    List<Map<String, Object>> exportRows(@Param("status") String status,
                                         @Param("urgency") String urgency,
                                         @Param("faultType") String faultType,
                                         @Param("campus") String campus,
                                         @Param("keyword") String keyword,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}
