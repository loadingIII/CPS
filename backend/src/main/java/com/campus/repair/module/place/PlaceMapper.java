package com.campus.repair.module.place;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

@Mapper
public interface PlaceMapper extends BaseMapper<Place> {

    /** 写入位置坐标（POINT(lng lat)；不用地理 SRID 以避开 MySQL 轴序校验，仅做存取） */
    @Update("UPDATE place SET geo = ST_GeomFromText(CONCAT('POINT(', #{lng}, ' ', #{lat}, ')')) "
            + "WHERE id = #{id}")
    int setGeo(@Param("id") Long id, @Param("lng") Double lng, @Param("lat") Double lat);

    /** 读取位置坐标（geo 为 NULL 时返回 null） */
    @Select("SELECT ST_X(geo) AS lng, ST_Y(geo) AS lat FROM place WHERE id = #{id} AND geo IS NOT NULL")
    Map<String, Object> selectGeoById(@Param("id") Long id);
}
