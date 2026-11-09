package com.campus.repair.module.dict;

/** 校区字典项（供报修端选择器与维修工负责校区多选使用） */
public record CampusVO(String code, String name, Double lng, Double lat) {
}
