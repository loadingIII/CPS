package com.campus.repair.module.dict;

import java.util.Map;

/**
 * 校区字典（代码固定枚举，不落库、不需要管理端维护）。
 *
 * <p>位置模型中唯一受控的一层：报修人必须选择校区，其余楼栋 / 房间 / 公共设施
 * 由报修人自由填写文本。维修工的服务范围与派单 / 抢单资格也只按校区判定。
 * 每个校区带一个默认中心坐标，供未做地图选点的工单导航兜底
 * （坐标为校区近似中心，报修时地图选点优先于此）。</p>
 *
 * <p>校区清单（广东工业大学六校区）：大学城、龙洞、东风路、番禺、沙河、揭阳。
 * 增删校区请同步更新 {@code worker_scope.campus} / {@code repair_order.campus}
 * 中的历史 code，参见 design/migration_v2.2_campus.sql。</p>
 */
public enum Campus {

    UNIVERSITY_TOWN("大学城校区", "广州市番禺区大学城外环西路 100 号", 113.3990, 23.0390),
    LONGDONG("龙洞校区", "广州市天河区迎龙路 161 号", 113.3620, 23.1920),
    DONGFENG_ROAD("东风路校区", "广州市越秀区东风东路 729 号", 113.2930, 23.1310),
    PANYU("番禺校区", "广州市番禺区钟村街市广路钟三路段 11 号", 113.3130, 22.9880),
    SHAHE("沙河校区", "广州市天河区先烈东路 131 号", 113.3100, 23.1510),
    JIEYANG("揭阳校区", "揭阳市惠来县粤东新城大学路", 116.1780, 23.0320);

    private final String label;
    private final String address;
    private final double lng;
    private final double lat;

    Campus(String label, String address, double lng, double lat) {
        this.label = label;
        this.address = address;
        this.lng = lng;
        this.lat = lat;
    }

    public String label() { return label; }

    public String address() { return address; }

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

    /** 校区详细地址；非法 code 返回空串，便于前端直接展示 */
    public static String addressOf(String code) {
        Campus c = of(code);
        return c == null ? "" : c.address();
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
