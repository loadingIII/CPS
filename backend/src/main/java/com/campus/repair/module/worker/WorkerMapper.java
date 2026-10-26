package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface WorkerMapper {

    /** 维修工分页（用户 + 档案联查），skills/scopes/currentLoad 由服务层补充 */
    @Select("""
            <script>
            SELECT u.id, u.job_no AS jobNo, u.name, u.phone,
                   p.on_duty AS onDuty, p.load_limit AS loadLimit, p.score
            FROM sys_user u
            JOIN worker_profile p ON p.user_id = u.id
            WHERE u.status != 3
            <if test="keyword != null and keyword != ''">
              AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR u.job_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="onDuty != null"> AND p.on_duty = #{onDuty}</if>
            <if test="skillId != null">
              AND EXISTS (SELECT 1 FROM worker_skill ws WHERE ws.user_id = u.id AND ws.skill_id = #{skillId})
            </if>
            ORDER BY u.id
            </script>
            """)
    IPage<WorkerVO> selectWorkerPage(Page<WorkerVO> page, @Param("keyword") String keyword,
                                     @Param("onDuty") Boolean onDuty, @Param("skillId") Long skillId);

    /** 在办负载统计（status IN (PENDING, PROCESSING)），§2.5 负载规则 */
    @Select("""
            <script>
            SELECT worker_id AS workerId, COUNT(*) AS cnt
            FROM repair_order
            WHERE status IN ('PENDING', 'PROCESSING') AND worker_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY worker_id
            </script>
            """)
    List<Map<String, Object>> countLoad(@Param("ids") List<Long> ids);

    /** 重算综合评分 = 该维修工全部已评价工单评价均值（无评价回退 5.00），§2.6 */
    @Update("""
            UPDATE worker_profile SET score = IFNULL((
                SELECT ROUND(AVG(e.rating), 2)
                FROM evaluation e
                JOIN repair_order o ON e.order_id = o.id
                WHERE o.worker_id = #{workerId} AND o.status = 'EVALUATED'
            ), 5.00)
            WHERE user_id = #{workerId}
            """)
    int recalcScore(@Param("workerId") Long workerId);
}
