package com.campus.repair.module.stats;

import com.campus.repair.module.order.RepairOrderMapper;
import com.campus.repair.module.stats.dto.OntimePointVO;
import com.campus.repair.module.stats.dto.WorkerPerfVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R2 统计口径（与《第二阶段计划书》3.6 一致）：
 * 按时完成率 = due_at 前完工数 / 窗口内已完工数；分母按 completed_at 落在窗口内。
 */
@Service
public class StatsService {

    private static final int MIN_SAMPLE = 5;

    private final RepairOrderMapper orderMapper;

    public StatsService(RepairOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    public List<OntimePointVO> ontime(int days) {
        int window = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(window - 1L).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        Map<String, Long> submitted = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countBySubmittedDay(from, to)) {
            submitted.put(String.valueOf(row.get("day")), number(row.get("total")));
        }
        Map<String, long[]> completed = new HashMap<>();
        for (Map<String, Object> row : orderMapper.countOntimeByDay(from, to)) {
            completed.put(String.valueOf(row.get("day")),
                    new long[]{number(row.get("done")), number(row.get("onTime"))});
        }

        List<OntimePointVO> result = new ArrayList<>(window);
        for (int offset = window - 1; offset >= 0; offset--) {
            String day = today.minusDays(offset).toString();
            long[] c = completed.getOrDefault(day, new long[]{0, 0});
            OntimePointVO vo = new OntimePointVO();
            vo.setDay(day);
            vo.setSubmitted(submitted.getOrDefault(day, 0L));
            vo.setCompleted(c[0]);
            vo.setOnTime(c[1]);
            vo.setOnTimeRate(c[0] == 0 ? null
                    : BigDecimal.valueOf(c[1]).divide(BigDecimal.valueOf(c[0]), 4, RoundingMode.HALF_UP)
                            .doubleValue());
            result.add(vo);
        }
        return result;
    }

    public List<WorkerPerfVO> workerPerformance(int days) {
        int window = Math.max(1, Math.min(days, 180));
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();
        List<WorkerPerfVO> result = new ArrayList<>();
        for (Map<String, Object> row : orderMapper.selectWorkerPerformance(from)) {
            WorkerPerfVO vo = new WorkerPerfVO();
            vo.setWorkerId(((Number) row.get("workerId")).longValue());
            vo.setName(row.get("name") == null ? "未知" : String.valueOf(row.get("name")));
            vo.setDone(number(row.get("done")));
            vo.setOnTime(number(row.get("onTime")));
            vo.setOnTimeRate(vo.getDone() == 0 ? null
                    : BigDecimal.valueOf(vo.getOnTime())
                            .divide(BigDecimal.valueOf(vo.getDone()), 4, RoundingMode.HALF_UP).doubleValue());
            Object avg = row.get("avgRating");
            vo.setAvgRating(avg == null ? null : ((Number) avg).doubleValue());
            Object score = row.get("score");
            vo.setScore(score == null ? null : new BigDecimal(String.valueOf(score)));
            vo.setSampleEnough(vo.getDone() >= MIN_SAMPLE);
            result.add(vo);
        }
        // 样本充足的优先排名，其次按完成量
        result.sort((a, b) -> {
            if (a.isSampleEnough() != b.isSampleEnough()) {
                return a.isSampleEnough() ? -1 : 1;
            }
            return Long.compare(b.getDone(), a.getDone());
        });
        return result;
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
