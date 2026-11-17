-- ---------------------------------------------------------------------
-- 迁移脚本：派单与抢单调度重构（v2.1）
-- 适用：已按旧版 schema.sql 建库、且不希望重建数据库的环境
-- 说明：全新环境直接用 design/schema.sql 即可，无需执行本脚本
-- ---------------------------------------------------------------------

ALTER TABLE repair_order
  ADD COLUMN finish_summary    VARCHAR(500) NULL COMMENT '完工维修说明（故障原因/处理方式）' AFTER version,
  ADD COLUMN dispatch_source   ENUM('MANUAL','GRAB','AUTO') NULL COMMENT '最近一次派单来源' AFTER finish_summary,
  ADD COLUMN grab_open_at      DATETIME NULL COMMENT '抢单池开放时间（紧急单=审核时，普通单=延迟开放）' AFTER dispatch_source,
  ADD COLUMN assign_expires_at DATETIME NULL COMMENT '本次派单的接单截止时间（超时自动回收）' AFTER grab_open_at,
  ADD COLUMN due_at            DATETIME NULL COMMENT '处理截止时间（接单/抢单时按 SLA 写入）' AFTER assign_expires_at,
  ADD COLUMN dispatch_count    INT NOT NULL DEFAULT 0 COMMENT '累计派单/抢单次数' AFTER due_at,
  ADD COLUMN reject_count      INT NOT NULL DEFAULT 0 COMMENT '累计被拒/超时回收次数' AFTER dispatch_count,
  ADD KEY idx_pool (status, worker_id, grab_open_at),
  ADD KEY idx_offer_expire (status, assign_expires_at),
  ADD KEY idx_due (status, due_at);

ALTER TABLE worker_skill
  ADD COLUMN level ENUM('PRIMARY','SECONDARY') NOT NULL DEFAULT 'PRIMARY'
      COMMENT '主修/兼修（派单评分因子）' AFTER skill_id;

-- 存量数据回填：已审核未完结的工单补抢单开放时间与处理截止时间
UPDATE repair_order
SET grab_open_at = IFNULL(audited_at, submitted_at)
WHERE status = 'PENDING' AND worker_id IS NULL AND grab_open_at IS NULL;

UPDATE repair_order
SET due_at = DATE_ADD(accepted_at, INTERVAL IF(urgency = 'URGENT', 4, 24) HOUR)
WHERE status = 'PROCESSING' AND accepted_at IS NOT NULL AND due_at IS NULL;

UPDATE repair_order
SET assign_expires_at = DATE_ADD(assigned_at, INTERVAL IF(urgency = 'URGENT', 30, 120) MINUTE)
WHERE status = 'PENDING' AND worker_id IS NOT NULL AND assigned_at IS NOT NULL AND assign_expires_at IS NULL;

UPDATE repair_order
SET dispatch_count = 1
WHERE worker_id IS NOT NULL AND dispatch_count = 0;

-- 存量完工说明从 order_log 回填（旧版本只把维修说明写进了日志备注）
UPDATE repair_order o
JOIN order_log l ON l.order_id = o.id AND l.action = '完工'
SET o.finish_summary = l.remark
WHERE o.finish_summary IS NULL AND l.remark IS NOT NULL;

-- 演示数据：按「第一项主修、其余兼修」的约定回填技能等级
UPDATE worker_skill ws
JOIN sys_user u ON u.id = ws.user_id
JOIN skill s ON s.id = ws.skill_id
SET ws.level = 'SECONDARY'
WHERE (u.job_no = 'worker01' AND s.name = '空调家电')
   OR (u.job_no = 'worker05' AND s.name = '电梯')
   OR (u.job_no = 'worker06' AND s.name = '木工门窗');
