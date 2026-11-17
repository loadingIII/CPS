-- =====================================================================
-- 校园报修与维修工单管理系统 · 建表脚本
-- 环境：MySQL 8.0 / InnoDB / utf8mb4
-- 依据：《业务需求分析.md》§9 数据模型 / §5 状态机 / §7 派单调度
-- 对应：design/数据库设计.md（字段说明与设计取舍）
-- v2.0：位置树已移除，校区改为代码枚举。升级请重建数据库
--       （DROP DATABASE campus_repair 后重新执行本脚本）。
-- v3.0：R3 增加 AI 建议字段、知识库来源字段、kb_event 表；kb_article 支持草稿状态。
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS campus_repair DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE campus_repair;

-- ---------------------------------------------------------------------
-- 1. 用户表（统一用户 + RBAC 四角色）
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  job_no        VARCHAR(32)  NOT NULL COMMENT '学工号/工号（登录名）',
  name          VARCHAR(64)  NOT NULL COMMENT '姓名',
  password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希（BCrypt）',
  phone         VARCHAR(20)  NULL COMMENT '联系电话',
  role          ENUM('REPORTER','ADMIN','REPAIRER','SYS_ADMIN') NOT NULL COMMENT '角色：报修人/后勤管理员/维修工/系统管理员',
  department    VARCHAR(128) NULL COMMENT '部门（教职工）',
  address       VARCHAR(128) NULL COMMENT '住址（学生：宿舍楼栋·房间，与部门二选一填写）',
  campus        VARCHAR(32)  NULL COMMENT '所属校区（Campus code，报修人资料）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 2停用 3注销',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_no (job_no),
  KEY idx_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（统一用户+角色区分）';

-- ---------------------------------------------------------------------
-- 2. 校区字典（v2.0：改为代码枚举 Campus，不建表、无需管理端维护）
--    报修时校区必选；楼栋 / 房间 / 公共点位由报修人自由填写文本。
--    维修工负责校区、派单与抢单资格均只按此枚举判定。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 3. 维修工档案表（1:1 扩展 sys_user）
-- ---------------------------------------------------------------------
CREATE TABLE worker_profile (
  user_id    BIGINT       NOT NULL COMMENT '用户编号（=维修工）',
  on_duty    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '在岗状态',
  load_limit INT          NOT NULL DEFAULT 5 COMMENT '负载上限（在办工单数）',
  score      DECIMAL(3,2) NOT NULL DEFAULT 5.00 COMMENT '综合评分（1.00~5.00）',
  PRIMARY KEY (user_id),
  CONSTRAINT fk_worker_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工档案表（当前负载不落库，实时 COUNT）';

-- ---------------------------------------------------------------------
-- 4. 技能字典表
-- ---------------------------------------------------------------------
CREATE TABLE skill (
  id      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '技能编号',
  name    VARCHAR(32) NOT NULL COMMENT '技能名称（水电/木工门窗/网络多媒体/空调家电/电梯/实验设备...）',
  enabled TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '启用状态',
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能字典表';

-- ---------------------------------------------------------------------
-- 5. 维修工-技能关联表（N:M）
-- ---------------------------------------------------------------------
CREATE TABLE worker_skill (
  user_id  BIGINT NOT NULL COMMENT '维修工',
  skill_id BIGINT NOT NULL COMMENT '技能',
  level    ENUM('PRIMARY','SECONDARY') NOT NULL DEFAULT 'PRIMARY' COMMENT '主修/兼修（派单评分因子）',
  PRIMARY KEY (user_id, skill_id),
  KEY idx_skill (skill_id),
  CONSTRAINT fk_ws_user  FOREIGN KEY (user_id)  REFERENCES sys_user (id),
  CONSTRAINT fk_ws_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工-技能关联表';

-- ---------------------------------------------------------------------
-- 6. 维修工负责校区表（校区枚举 code）
-- ---------------------------------------------------------------------
CREATE TABLE worker_scope (
  user_id BIGINT      NOT NULL COMMENT '维修工',
  campus  VARCHAR(32) NOT NULL COMMENT '校区枚举 code（六校区，见 Campus）',
  PRIMARY KEY (user_id, campus),
  KEY idx_campus (campus),
  CONSTRAINT fk_scope_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工负责校区表';

-- ---------------------------------------------------------------------
-- 7. 报修工单表（核心实体）
-- ---------------------------------------------------------------------
CREATE TABLE repair_order (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '工单编号',
  order_no      VARCHAR(16)   NOT NULL COMMENT '工单号（BX+日期+4位序号，如 BX202610120001）',
  reporter_id   BIGINT        NOT NULL COMMENT '报修人',
  worker_id     BIGINT        NULL COMMENT '承接维修工（派单后回填，可空）',
  campus        VARCHAR(32)   NOT NULL COMMENT '校区枚举 code（报修必选）',
  location_text VARCHAR(255)  NOT NULL COMMENT '完整位置串：校区名 · 报修人自由填写的详细位置',
  map_name      VARCHAR(128)  NULL COMMENT '地图选点 POI 名称/地址（补充定位，DB-02 v1.2）',
  map_lng       DECIMAL(10,6) NULL COMMENT '地图选点经度（DB-02 v1.2）',
  map_lat       DECIMAL(10,6) NULL COMMENT '地图选点纬度（DB-02 v1.2）',
  fault_type    VARCHAR(32)   NOT NULL COMMENT '故障类型（字典：水电/木工门窗/网络多媒体/空调家电/电梯/实验设备/其他）',
  urgency       ENUM('NORMAL','URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '紧急度（紧急单优先调度）',
  description   VARCHAR(1000) NOT NULL COMMENT '故障描述',
  status        ENUM('PENDING_AUDIT','PENDING','PROCESSING','COMPLETED','EVALUATED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING_AUDIT' COMMENT '状态：待审核/待处理/处理中/已完成/已评价/已驳回/已取消（§5 状态机）',
  is_overdue    TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '超时标记位（非独立状态）',
  main_order_id BIGINT        NULL COMMENT '关联主单（重复报修合并时指向主单，自关联）',
  source_kb_id  BIGINT        NULL COMMENT '从知识库「未解决去报修」进入时记录的文章 id（逻辑关联）',
  ai_suggested_type VARCHAR(32) NULL COMMENT 'AI 建议的故障类型（未使用建议时为 NULL）',
  ai_suggested_urgency VARCHAR(16) NULL COMMENT 'AI 建议的紧急度：NORMAL/URGENT',
  ai_suggestion_used TINYINT(1) NULL COMMENT '用户是否点击采纳过 AI 建议',
  version       INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（并发接单/派单幂等）',
  finish_summary VARCHAR(500) NULL COMMENT '完工维修说明（故障原因/处理方式）',
  dispatch_source ENUM('MANUAL','GRAB','AUTO') NULL COMMENT '最近一次派单来源（GRAB 仅历史数据）',
  assign_expires_at DATETIME  NULL COMMENT '本次派单的接单截止时间（超时自动回收）',
  due_at        DATETIME      NULL COMMENT '处理截止时间（接单时按 SLA 写入）',
  dispatch_count INT          NOT NULL DEFAULT 0 COMMENT '累计派单次数（识别难派单）',
  reject_count  INT           NOT NULL DEFAULT 0 COMMENT '累计被拒/超时回收次数',
  submitted_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  audited_at    DATETIME      NULL COMMENT '审核时间',
  assigned_at   DATETIME      NULL COMMENT '本次派单时间（回池时清空，历史见 order_log）',
  accepted_at   DATETIME      NULL COMMENT '接单时间（回池时清空）',
  completed_at  DATETIME      NULL COMMENT '完工时间',
  rated_at      DATETIME      NULL COMMENT '评价时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status_submitted (status, submitted_at),
  KEY idx_worker_status (worker_id, status),
  KEY idx_worker_completed (worker_id, completed_at),
  KEY idx_pool (status, worker_id),
  KEY idx_offer_expire (status, assign_expires_at),
  KEY idx_due (status, due_at),
  KEY idx_reporter (reporter_id),
  KEY idx_campus (campus),
  KEY idx_campus_type_completed (campus, fault_type, completed_at),
  CONSTRAINT fk_order_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id),
  CONSTRAINT fk_order_worker   FOREIGN KEY (worker_id)   REFERENCES sys_user (id),
  CONSTRAINT fk_order_main     FOREIGN KEY (main_order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修工单表（核心实体）';

-- ---------------------------------------------------------------------
-- 8. 附件表（报修图/完工图）
-- ---------------------------------------------------------------------
CREATE TABLE attachment (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '附件编号',
  order_id   BIGINT       NOT NULL COMMENT '所属工单',
  url        VARCHAR(512) NOT NULL COMMENT '文件URL',
  type       ENUM('REPORT','FINISH') NOT NULL COMMENT '附件类型：报修图/完工图',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  CONSTRAINT fk_att_order FOREIGN KEY (order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件表（工单图片）';

-- ---------------------------------------------------------------------
-- 9. 工单日志表（状态时间线数据源 F6-1，关键操作留痕）
-- ---------------------------------------------------------------------
CREATE TABLE order_log (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  order_id      BIGINT      NOT NULL COMMENT '所属工单',
  action        VARCHAR(32) NOT NULL COMMENT '操作动作（提交/审核通过/驳回/合并/派单/接单/拒单/转单/完工/评价/催单/超时预警/归档）',
  operator_id   BIGINT      NULL COMMENT '操作人（系统动作为 NULL）',
  operator_role ENUM('REPORTER','ADMIN','REPAIRER','SYS_ADMIN','SYSTEM') NOT NULL COMMENT '操作人角色',
  remark        VARCHAR(500) NULL COMMENT '备注（驳回理由/拒单理由等）',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  CONSTRAINT fk_log_order FOREIGN KEY (order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单日志表（状态时间线）';

-- ---------------------------------------------------------------------
-- 10. 评价表（一单一评价，uk_order 硬保证）
-- ---------------------------------------------------------------------
CREATE TABLE evaluation (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价编号',
  order_id   BIGINT       NOT NULL COMMENT '所属工单（一单一评价）',
  rating     TINYINT      NOT NULL COMMENT '星级 1~5',
  tags       VARCHAR(255) NULL COMMENT '评价标签（速度快/态度好/专业/需改进，逗号分隔）',
  content    VARCHAR(500) NULL COMMENT '评价内容',
  is_default TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否 7 天默认好评（系统自动关闭）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id),
  CONSTRAINT fk_eval_order FOREIGN KEY (order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表（一单一评价）';

-- ---------------------------------------------------------------------
-- 11. 配件消耗表（可选）
-- ---------------------------------------------------------------------
CREATE TABLE part_usage (
  id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  order_id  BIGINT      NOT NULL COMMENT '所属工单',
  part_name VARCHAR(64) NOT NULL COMMENT '配件名称',
  quantity  INT         NOT NULL DEFAULT 1 COMMENT '数量',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  CONSTRAINT fk_part_order FOREIGN KEY (order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配件消耗表';

-- ---------------------------------------------------------------------
-- 12. 站内通知表
-- ---------------------------------------------------------------------
CREATE TABLE notification (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知编号',
  receiver_id BIGINT       NOT NULL COMMENT '接收人',
  type        VARCHAR(32)  NOT NULL COMMENT '通知类型（状态变更/派单/催单/超时预警/回访任务...）',
  content     VARCHAR(500) NOT NULL COMMENT '内容',
  is_read     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '已读',
  biz_id      BIGINT       NULL COMMENT '关联业务 id（工单 id，便于前端跳转）',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_receiver_read (receiver_id, is_read),
  CONSTRAINT fk_noti_receiver FOREIGN KEY (receiver_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- ---------------------------------------------------------------------
-- 13. 维修知识库文章表
-- ---------------------------------------------------------------------
CREATE TABLE kb_article (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文章编号',
  title      VARCHAR(128) NOT NULL COMMENT '标题（默认取上传文件名）',
  content    TEXT         NOT NULL COMMENT 'Markdown 正文',
  view_count INT          NOT NULL DEFAULT 0 COMMENT '浏览量',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0停用 1启用 2草稿',
  source_order_id BIGINT  NULL COMMENT '由哪张工单沉淀而来（逻辑关联）',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_source_order (source_order_id),
  KEY idx_kb_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修知识库文章表';

-- ---------------------------------------------------------------------
-- 13b. 知识库自助事件表（R3：已解决 / 未解决去报修 / 搜索无结果）
-- ---------------------------------------------------------------------
CREATE TABLE kb_event (
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

-- ---------------------------------------------------------------------
-- 14. 超时预警表（预警事件持久化；is_overdue 为工单标记位，职责分离）
-- ---------------------------------------------------------------------
CREATE TABLE overdue_alert (
  id         BIGINT     NOT NULL AUTO_INCREMENT COMMENT '预警编号',
  order_id   BIGINT     NOT NULL COMMENT '所属工单',
  stage      ENUM('ACCEPT','PROCESS') NOT NULL COMMENT '预警阶段：接单超时/处理超时',
  level      TINYINT    NOT NULL DEFAULT 1 COMMENT '预警级别（连续超时升级，F8-3）',
  handled    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '已处理',
  created_at DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_handled (handled),
  CONSTRAINT fk_alert_order FOREIGN KEY (order_id) REFERENCES repair_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='超时预警表';

-- ---------------------------------------------------------------------
-- 15. 差评回访任务表（R2-F7-3：评价 ≤2 星自动生成）
-- ---------------------------------------------------------------------
CREATE TABLE revisit_task (
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

SET FOREIGN_KEY_CHECKS = 1;
