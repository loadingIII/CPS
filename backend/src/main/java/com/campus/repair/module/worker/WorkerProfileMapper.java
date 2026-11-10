package com.campus.repair.module.worker;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkerProfileMapper extends BaseMapper<WorkerProfile> {

    /**
     * 锁定维修工容量行（容量令牌）。
     * 派单/抢单事务的第一条读，后续 COUNT 才能看到前一个持锁事务已提交的数据。
     */
    @Select("SELECT * FROM worker_profile WHERE user_id = #{userId} FOR UPDATE")
    WorkerProfile selectForUpdate(@Param("userId") Long userId);
}
