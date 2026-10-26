package com.campus.repair.module.place;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("place")
public class Place {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父节点id：CAMPUS 为 NULL */
    private Long parentId;

    /** 冗余：沿父链取根校区 id */
    private Long campusId;

    private PlaceType type;

    private String name;

    private String code;

    private Boolean enabled;
}
