-- =====================================================================
-- 校园报修系统 · v2.2 校区清单迁移（2026-11-10）
--
-- 背景：Campus 枚举由三个示例校区（EAST/WEST/SOUTH）替换为六校区，
--       校区 code 是字符串，直接存在 repair_order.campus 与 worker_scope.campus 中。
--       不迁移会导致历史工单详情显示「未知校区」、维修工负责范围失效。
--
-- 映射：EAST  -> UNIVERSITY_TOWN  大学城校区
--       WEST  -> LONGDONG         龙洞校区
--       SOUTH -> PANYU            番禺校区
--       另外三校区（DONGFENG_ROAD / SHAHE / JIEYANG）为新增，无历史数据。
--
-- 幂等：所有 UPDATE 只匹配旧 code；INSERT 使用 IGNORE，可重复执行。
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 campus_repair < migration_v2.2_campus.sql
-- 注意：执行后需重启后端，让新的 Campus 枚举生效。
-- =====================================================================
SET NAMES utf8mb4;
USE campus_repair;

START TRANSACTION;

-- 1. 工单校区 code
UPDATE repair_order SET campus = 'UNIVERSITY_TOWN' WHERE campus = 'EAST';
UPDATE repair_order SET campus = 'LONGDONG'        WHERE campus = 'WEST';
UPDATE repair_order SET campus = 'PANYU'           WHERE campus = 'SOUTH';

-- 2. 冗余位置串前缀（location_text = 「校区名 · 详细位置」，列表页免 JOIN）
UPDATE repair_order
   SET location_text = CONCAT('大学城校区 · ', SUBSTRING(location_text, CHAR_LENGTH('东校区 · ') + 1))
 WHERE location_text LIKE '东校区 · %';
UPDATE repair_order
   SET location_text = CONCAT('龙洞校区 · ', SUBSTRING(location_text, CHAR_LENGTH('西校区 · ') + 1))
 WHERE location_text LIKE '西校区 · %';
UPDATE repair_order
   SET location_text = CONCAT('番禺校区 · ', SUBSTRING(location_text, CHAR_LENGTH('南校区 · ') + 1))
 WHERE location_text LIKE '南校区 · %';

-- 3. 维修工负责校区 code（复合主键 user_id+campus，此映射不会产生重复键）
UPDATE worker_scope SET campus = 'UNIVERSITY_TOWN' WHERE campus = 'EAST';
UPDATE worker_scope SET campus = 'LONGDONG'        WHERE campus = 'WEST';
UPDATE worker_scope SET campus = 'PANYU'           WHERE campus = 'SOUTH';

-- 4. 与 SeedRunner 对齐：给跨校区驻点补两条范围记录（揭阳校区暂无驻点维修工）
INSERT IGNORE INTO worker_scope (user_id, campus)
SELECT id, 'DONGFENG_ROAD' FROM sys_user WHERE job_no = 'worker03';
INSERT IGNORE INTO worker_scope (user_id, campus)
SELECT id, 'SHAHE' FROM sys_user WHERE job_no = 'worker05';

COMMIT;

-- ---------------- 验证输出 ----------------
SELECT 'repair_order' AS tbl, campus, COUNT(*) AS total FROM repair_order GROUP BY campus ORDER BY campus;
SELECT 'worker_scope' AS tbl, campus, COUNT(*) AS total FROM worker_scope GROUP BY campus ORDER BY campus;
SELECT id, order_no, campus, location_text FROM repair_order ORDER BY id DESC LIMIT 5;
