package com.campus.repair.module.place;

/**
 * 位置类型：校区 / 片区 / 楼栋 / 具体点位。
 * 层级链：CAMPUS → AREA → BUILDING → SPOT。
 */
public enum PlaceType {
    CAMPUS, AREA, BUILDING, SPOT;

    /** 上一层级类型；CAMPUS 无父级 */
    public PlaceType parentType() {
        return switch (this) {
            case AREA -> CAMPUS;
            case BUILDING -> AREA;
            case SPOT -> BUILDING;
            case CAMPUS -> null;
        };
    }
}
