-- =====================================================================
-- R3（第三阶段）增量迁移：AI 报修建议 + 知识库自助闭环 + 案例沉淀
-- 适用：已执行 v2.6 的库；脚本通过 information_schema 判断列/索引是否存在，
--       可重复执行。执行前建议先备份，执行后重启后端。
-- =====================================================================
USE campus_repair;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 0. 辅助过程：按需加列 / 加索引 / 加唯一键
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS r3_add_column;
DELIMITER $$
CREATE PROCEDURE r3_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(600))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS r3_add_index;
DELIMITER $$
CREATE PROCEDURE r3_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(300))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (', p_cols, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS r3_add_unique;
DELIMITER $$
CREATE PROCEDURE r3_add_unique(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(300))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_cols, ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. repair_order：知识库来源 + AI 建议记录 + 返修查询索引
-- ---------------------------------------------------------------------
CALL r3_add_column('repair_order', 'source_kb_id',
  '`source_kb_id` BIGINT NULL COMMENT ''从知识库「未解决去报修」进入时记录的文章 id（逻辑关联，不建外键）''');
CALL r3_add_column('repair_order', 'ai_suggested_type',
  '`ai_suggested_type` VARCHAR(32) NULL COMMENT ''AI 建议的故障类型（未使用建议时为 NULL）''');
CALL r3_add_column('repair_order', 'ai_suggested_urgency',
  '`ai_suggested_urgency` VARCHAR(16) NULL COMMENT ''AI 建议的紧急度：NORMAL/URGENT''');
CALL r3_add_column('repair_order', 'ai_suggestion_used',
  '`ai_suggestion_used` TINYINT(1) NULL COMMENT ''用户是否点击采纳过 AI 建议''');
CALL r3_add_index('repair_order', 'idx_campus_type_completed',
  '`campus`, `fault_type`, `completed_at`');

-- ---------------------------------------------------------------------
-- 2. kb_article：状态扩展为 0 停用 / 1 启用 / 2 草稿；记录来源工单
-- ---------------------------------------------------------------------
ALTER TABLE kb_article
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用 2草稿';

CALL r3_add_column('kb_article', 'source_order_id',
  '`source_order_id` BIGINT NULL COMMENT ''由哪张工单沉淀而来（逻辑关联，不建外键）''');
CALL r3_add_unique('kb_article', 'uk_kb_source_order', '`source_order_id`');

-- ---------------------------------------------------------------------
-- 3. kb_event：教程页「已解决 / 未解决」与搜索无结果事件
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_event (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件编号',
  article_id BIGINT       NULL COMMENT '关联文章（SEARCH_MISS 为空）',
  user_id    BIGINT       NULL COMMENT '触发用户（可为空）',
  event_type ENUM('SOLVED','UNSOLVED','SEARCH_MISS') NOT NULL COMMENT '已解决/未解决去报修/搜索无结果',
  keyword    VARCHAR(64)  NULL COMMENT '搜索关键词（SEARCH_MISS 用）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_kb_event_article (article_id, event_type, created_at),
  KEY idx_kb_event_type (event_type, created_at),
  KEY idx_kb_event_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库自助事件表';

DROP PROCEDURE IF EXISTS r3_add_column;
DROP PROCEDURE IF EXISTS r3_add_index;
DROP PROCEDURE IF EXISTS r3_add_unique;
