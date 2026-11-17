-- =====================================================================
-- R2（第二阶段）增量迁移：通知跳转字段 + 差评回访任务 + 绩效索引
-- 适用：已执行 v2.2 六校区基线的库；可重复执行前请先确认列/表不存在。
-- =====================================================================

-- 1. 通知表增加业务 id（前端据 biz_id 跳转工单详情）
ALTER TABLE notification
  ADD COLUMN biz_id BIGINT NULL COMMENT '关联业务 id（工单 id）' AFTER is_read;

-- 2. 差评回访任务（评价 ≤2 星自动生成）
CREATE TABLE IF NOT EXISTS revisit_task (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  order_id    BIGINT       NOT NULL COMMENT '关联工单',
  worker_id   BIGINT       NULL COMMENT '被评价维修工',
  rating      INT          NOT NULL COMMENT '本次评价星级（1-5）',
  status      ENUM('PENDING','HANDLED','CLOSED') NOT NULL DEFAULT 'PENDING' COMMENT '待回访/已回访/已关闭',
  handled_by  BIGINT       NULL COMMENT '处理管理员',
  handled_at  DATETIME     NULL COMMENT '处理时间',
  remark      VARCHAR(300) NULL COMMENT '回访说明',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_revisit_order (order_id),
  KEY idx_revisit_status (status, created_at),
  CONSTRAINT fk_revisit_order  FOREIGN KEY (order_id) REFERENCES repair_order (id),
  CONSTRAINT fk_revisit_worker FOREIGN KEY (worker_id) REFERENCES sys_user (id),
  CONSTRAINT fk_revisit_admin  FOREIGN KEY (handled_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='差评回访任务表';

-- 3. 绩效与按时率按完工时间聚合的覆盖索引
ALTER TABLE repair_order
  ADD KEY idx_worker_completed (worker_id, completed_at);
