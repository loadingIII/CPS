package com.campus.repair.module.kb;

import java.util.List;

/**
 * 知识库自助统计（R3）。
 *
 * <p>自助解决率 = SOLVED / (SOLVED + UNSOLVED)，只统计点击了口径按钮的访问，
 * 不把“看完就走”推断为已解决。</p>
 */
public record KbStatsVO(long solved,
                        long unsolved,
                        long searchMiss,
                        Double selfServiceRate,
                        List<ArticleStat> topViewed,
                        List<ArticleStat> topUnsolved,
                        List<KeywordStat> searchMissKeywords) {

    public record ArticleStat(Long id, String title, long views,
                              long solved, long unsolved, Double unsolvedRate) {
    }

    public record KeywordStat(String keyword, long count) {
    }
}
