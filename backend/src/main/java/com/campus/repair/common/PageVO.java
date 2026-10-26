package com.campus.repair.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应 {list, total}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {

    private List<T> list;
    private long total;

    public static <S, T> PageVO<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageVO<>(page.getRecords().stream().map(mapper).toList(), page.getTotal());
    }
}
