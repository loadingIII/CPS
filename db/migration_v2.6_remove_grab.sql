-- ============================================================
-- v2.6 移除抢单业务：只保留管理端定向派单
-- 1) 删除抢单池开放时间字段及其复合索引；
-- 2) dispatch_source 保留 GRAB 枚举值，仅用于兼容历史工单，不再产生新值。
-- 执行前请先停止后端（旧进程仍在写 grab_open_at，会报 Unknown column）。
-- ============================================================

ALTER TABLE repair_order DROP INDEX idx_pool;
ALTER TABLE repair_order DROP COLUMN grab_open_at;
ALTER TABLE repair_order ADD KEY idx_pool (status, worker_id);

-- 历史抢单工单的派单来源保持原值，便于追溯；如需统一口径可执行：
-- UPDATE repair_order SET dispatch_source = 'MANUAL' WHERE dispatch_source = 'GRAB';
