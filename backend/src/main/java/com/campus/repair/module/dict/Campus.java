package com.campus.repair.module.dict;

import java.util.Map;

/**
 * 校区字典（代码固定枚举，不落库、不需要管理端维护）。
 *
 * <p>位置模型中唯一受控的一层：报修人必须选择校区，其余楼栋 / 房间 / 公共设施
 * 由报修人自由填写文本。维修工的服务范围与派单 / 抢单资格也只按校区判定。
 * 每个校区带一个默认中心坐标，供未做地图选点的工单导航兜底。</p>
 */
public enum Campus {

    EAST("东校区", 114.4000, 30.5000),
    WEST("西校区", 114.3600, 30.5120),
    SOUTH("南校区", 114.4230, 30.4780);

    private final String label;
    private final double lng;
    private final double lat;

    Campus(String label, double lng, double lat) {
        this.label = label;
        this.lng = lng;
        this.lat = lat;
    }

    public String label() { return label; }

    public double lng() { return lng; }

    public double lat() { return lat; }

    /** 宽松解析：非法 code 返回 null，交由调用方转业务异常 */
    public static Campus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (Campus c : values()) {
            if (c.name().equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        Campus c = of(code);
        return c == null ? "未知校区" : c.label();
    }

    /** 位置展示串：校区名 · 报修人填写的详细位置 */
    public static String format(String code, String detail) {
        return labelOf(code) + " · " + (detail == null ? "" : detail.trim());
    }

    /** 导航兜底坐标（地图选点优先于此） */
    public static Map<String, Object> geo(String code) {
        Campus c = of(code);
        return c == null ? null : Map.of("lng", c.lng(), "lat", c.lat());
    }
}
