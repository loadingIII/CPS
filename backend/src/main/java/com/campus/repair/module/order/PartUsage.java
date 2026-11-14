package com.campus.repair.module.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 配件消耗记录（R2-F5-5）。
 */
@Data
@TableName("part_usage")
public class PartUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String partName;

    private Integer quantity;
}
