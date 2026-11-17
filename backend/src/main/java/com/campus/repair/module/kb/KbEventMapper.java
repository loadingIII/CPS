package com.campus.repair.module.kb;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface KbEventMapper extends BaseMapper<KbEvent> {

    /** 按事件类型统计窗口内数量 */
    @Select("""
            SELECT event_type AS eventType, COUNT(*) AS total
            FROM kb_event
            WHERE created_at >= #{from} AND created_at < #{to}
            GROUP BY event_type
            """)
    List<Map<String, Object>> countByType(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /** 单篇文章的已解决/未解决计数 */
    @Select("""
            SELECT article_id AS articleId, event_type AS eventType, COUNT(*) AS total
            FROM kb_event
            WHERE created_at >= #{from} AND created_at < #{to}
              AND article_id IS NOT NULL
            GROUP BY article_id, event_type
            """)
    List<Map<String, Object>> countByArticle(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /** 搜索无结果词 TopN */
    @Select("""
            SELECT keyword, COUNT(*) AS total
            FROM kb_event
            WHERE event_type = 'SEARCH_MISS' AND created_at >= #{from} AND created_at < #{to}
              AND keyword IS NOT NULL AND keyword <> ''
            GROUP BY keyword
            ORDER BY total DESC, keyword ASC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topSearchMiss(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("limit") int limit);

    /** 同一用户同一文章 24 小时内是否已有同类事件（防刷数） */
    @Select("""
            SELECT COUNT(*) FROM kb_event
            WHERE article_id = #{articleId} AND user_id = #{userId}
              AND event_type = #{eventType} AND created_at >= #{since}
            """)
    long countRecent(@Param("articleId") Long articleId,
                     @Param("userId") Long userId,
                     @Param("eventType") String eventType,
                     @Param("since") LocalDateTime since);

    /** 同一用户同一关键词 24 小时内是否已记过搜索无结果 */
    @Select("""
            SELECT COUNT(*) FROM kb_event
            WHERE event_type = 'SEARCH_MISS' AND user_id = #{userId}
              AND keyword = #{keyword} AND created_at >= #{since}
            """)
    long countRecentSearchMiss(@Param("userId") Long userId,
                               @Param("keyword") String keyword,
                               @Param("since") LocalDateTime since);
}
