package com.campus.repair.module.stats.dto;

import java.util.List;

/**
 * AI 建议采纳率（R3）。
 *
 * <p>采纳标记由客户端回传，课程环境接受；生产环境应改为服务端签发 suggestionId。</p>
 */
public record AiSuggestionStatVO(long suggested,
                                 long used,
                                 Double acceptanceRate,
                                 List<TypeStat> byType) {

    public record TypeStat(String type, long suggested, long used) {
    }
}
