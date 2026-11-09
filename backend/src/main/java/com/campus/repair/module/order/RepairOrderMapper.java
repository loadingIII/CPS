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
}
