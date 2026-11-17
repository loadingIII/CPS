package com.campus.repair.module.order;

import com.campus.repair.common.BusinessException;
import com.campus.repair.module.dict.Campus;
import com.campus.repair.module.order.statemachine.OrderStatus;
import com.campus.repair.module.stats.StatsMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 工单明细导出（R3）：只做 Excel，单次最多 5000 行。
 *
 * <p>筛选条件与管理端工单列表一致；导出不改变任何业务数据。</p>
 */
@Service
public class OrderExportService {

    private static final int MAX_ROWS = 5000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StatsMapper statsMapper;

    public OrderExportService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public byte[] export(OrderStatus status, Urgency urgency, String faultType,
                         String campus, String keyword, LocalDate from, LocalDate to) {
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rows = statsMapper.exportRows(
                status == null ? null : status.name(),
                urgency == null ? null : urgency.name(),
                trim(faultType), trim(campus), trim(keyword), fromTime, toTime);
        if (rows.size() > MAX_ROWS) {
            throw BusinessException.param("导出最多 " + MAX_ROWS + " 行，请缩小时间范围");
        }

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("工单明细");
            String[] headers = {"工单号", "校区", "位置", "故障类型", "紧急度", "状态",
                    "报修人", "维修工", "提交时间", "审核时间", "接单时间", "完工时间",
                    "是否超时", "评价星级"};
            int[] widths = {20, 14, 34, 14, 10, 10, 12, 12, 18, 18, 18, 18, 10, 10};

            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, widths[i] * 256);
            }

            int index = 1;
            for (Map<String, Object> row : rows) {
                Row r = sheet.createRow(index++);
                set(r, 0, str(row.get("orderNo")));
                set(r, 1, Campus.labelOf(str(row.get("campus"))));
                set(r, 2, str(row.get("locationText")));
                set(r, 3, str(row.get("faultType")));
                set(r, 4, urgencyLabel(str(row.get("urgency"))));
                set(r, 5, statusLabel(str(row.get("status"))));
                set(r, 6, str(row.get("reporterName")));
                set(r, 7, str(row.get("workerName")));
                set(r, 8, time(row.get("submittedAt")));
                set(r, 9, time(row.get("auditedAt")));
                set(r, 10, time(row.get("acceptedAt")));
                set(r, 11, time(row.get("completedAt")));
                set(r, 12, Boolean.TRUE.equals(row.get("isOverdue")) ? "是" : "否");
                set(r, 13, row.get("rating") == null ? "—" : str(row.get("rating")));
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.conflict("导出失败：" + e.getMessage());
        }
    }

    private void set(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String urgencyLabel(String raw) {
        return "URGENT".equals(raw) ? "紧急" : "普通";
    }

    private String statusLabel(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            return OrderStatus.valueOf(raw).label();
        } catch (Exception e) {
            return raw;
        }
    }

    private String time(Object raw) {
        if (raw instanceof LocalDateTime dt) {
            return dt.format(TIME);
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(TIME);
        }
        return "";
    }
}
