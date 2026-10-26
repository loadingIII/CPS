package com.campus.repair.module.place.dto;

import com.campus.repair.module.place.PlaceType;

import java.util.List;

/**
 * 位置树节点（报修级联选择 / 管理端树形表格）。
 */
public record PlaceNodeVO(Long id, PlaceType type, String name, String code,
                          Boolean enabled, List<PlaceNodeVO> children) {
}
