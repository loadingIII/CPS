package com.campus.repair.module.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 维修工工单地图上的一个坐标点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapOrderVO {

    private Long id;
    private String orderNo;
    /** 状态中文标签 */
    private String status;
    private String urgency;
    private String locationText;
    private String faultType;
    /** 报修时的地图选点名称（可空） */
    private String mapName;
    private Double lng;
    private Double lat;
    /** MINE = 已接的单；GRAB = 可接的单 */
    private String kind;
}
