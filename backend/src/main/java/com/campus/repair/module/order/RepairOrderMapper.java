package com.campus.repair.module.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 当日工单号最大序号（工单号规则 §2.3：BX+yyyyMMdd+4位序号） */
    @Select("SELECT IFNULL(MAX(CAST(RIGHT(order_no, 4) AS UNSIGNED)), 0) "
            + "FROM repair_order WHERE order_no LIKE CONCAT(#{prefix}, '%')")
    int selectMaxSeqToday(@Param("prefix") String prefix);
}
