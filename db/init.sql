-- =====================================================================
-- 校园报修与维修工单管理系统 · 最终初始化脚本
-- 内容：建库 + 15 张表 + 演示数据（15 个账号 / 7 类技能 / 12 张工单）
-- 环境：MySQL 8.0 / InnoDB / utf8mb4
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < CPS/db/init.sql
--
-- 注意：
--   1) 本脚本会 DROP 并重建 campus_repair 库，执行前请备份；
--   2) 账号密码统一 123456（sa / admin1 / admin2 / worker01-06 /
--      student01-05 / teacher01）；
--   3) 演示工单时间基于 2026-11-17 生成，随着时间推移“剩余时间”会变化；
--   4) 只建空库用 schema.sql；已有旧库升级按 db/README.md 的顺序执行 migration；
--   5) 导入后启动后端即可登录，无需再等 SeedRunner 初始化。
-- =====================================================================

SET NAMES utf8mb4;

DROP DATABASE IF EXISTS `campus_repair`;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `campus_repair` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `campus_repair`;
DROP TABLE IF EXISTS `attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '附件编号',
  `order_id` bigint NOT NULL COMMENT '所属工单',
  `url` varchar(512) NOT NULL COMMENT '文件URL',
  `type` enum('REPORT','FINISH') NOT NULL COMMENT '附件类型：报修图/完工图',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `fk_att_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件表（工单图片）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attachment` WRITE;
/*!40000 ALTER TABLE `attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `attachment` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价编号',
  `order_id` bigint NOT NULL COMMENT '所属工单（一单一评价）',
  `rating` tinyint NOT NULL COMMENT '星级 1~5',
  `tags` varchar(255) DEFAULT NULL COMMENT '评价标签（速度快/态度好/专业/需改进，逗号分隔）',
  `content` varchar(500) DEFAULT NULL COMMENT '评价内容',
  `is_default` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否 7 天默认好评（系统自动关闭）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  CONSTRAINT `fk_eval_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价表（一单一评价）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `evaluation` WRITE;
/*!40000 ALTER TABLE `evaluation` DISABLE KEYS */;
INSERT INTO `evaluation` VALUES (1,8,5,NULL,'师傅很专业，处理很快！',0,'2026-11-14 18:23:42'),(2,9,4,NULL,'修好了，态度不错，稍慢了一点',0,'2026-11-13 22:23:42');
/*!40000 ALTER TABLE `evaluation` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `kb_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_article` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文章编号',
  `title` varchar(128) NOT NULL COMMENT '标题（默认取上传文件名）',
  `content` text NOT NULL COMMENT 'Markdown 正文',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0停用 1启用 2草稿',
  `source_order_id` bigint DEFAULT NULL COMMENT '由哪张工单沉淀而来（逻辑关联）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_source_order` (`source_order_id`),
  KEY `idx_kb_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修知识库文章表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `kb_article` WRITE;
/*!40000 ALTER TABLE `kb_article` DISABLE KEYS */;
/*!40000 ALTER TABLE `kb_article` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `kb_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件编号',
  `article_id` bigint DEFAULT NULL COMMENT '关联文章（SEARCH_MISS 为空）',
  `user_id` bigint DEFAULT NULL COMMENT '触发用户（可为空）',
  `event_type` enum('SOLVED','UNSOLVED','SEARCH_MISS') NOT NULL COMMENT '已解决/未解决去报修/搜索无结果',
  `keyword` varchar(64) DEFAULT NULL COMMENT '搜索关键词（SEARCH_MISS 用）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_kb_event_article` (`article_id`,`event_type`,`created_at`),
  KEY `idx_kb_event_type` (`event_type`,`created_at`),
  KEY `idx_kb_event_keyword` (`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库自助事件表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `kb_event` WRITE;
/*!40000 ALTER TABLE `kb_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `kb_event` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '通知编号',
  `receiver_id` bigint NOT NULL COMMENT '接收人',
  `type` varchar(32) NOT NULL COMMENT '通知类型（状态变更/派单/催单/超时预警/回访任务...）',
  `content` varchar(500) NOT NULL COMMENT '内容',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '已读',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务 id（工单 id，便于前端跳转）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_read` (`receiver_id`,`is_read`),
  CONSTRAINT `fk_noti_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `order_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  `order_id` bigint NOT NULL COMMENT '所属工单',
  `action` varchar(32) NOT NULL COMMENT '操作动作（提交/审核通过/驳回/合并/派单/接单/拒单/转单/完工/评价/催单/超时预警/归档）',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人（系统动作为 NULL）',
  `operator_role` enum('REPORTER','ADMIN','REPAIRER','SYS_ADMIN','SYSTEM') NOT NULL COMMENT '操作人角色',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注（驳回理由/拒单理由等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `fk_log_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单日志表（状态时间线）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `order_log` WRITE;
/*!40000 ALTER TABLE `order_log` DISABLE KEYS */;
INSERT INTO `order_log` VALUES (1,1,'提交',10,'REPORTER',NULL,'2026-11-17 20:23:42'),(2,2,'提交',11,'REPORTER',NULL,'2026-11-17 18:23:42'),(3,3,'提交',12,'REPORTER',NULL,'2026-11-17 16:23:42'),(4,3,'审核通过',2,'ADMIN',NULL,'2026-11-17 16:53:42'),(5,4,'提交',10,'REPORTER',NULL,'2026-11-17 19:23:42'),(6,4,'审核通过',2,'ADMIN',NULL,'2026-11-17 19:35:42'),(7,4,'派单',2,'ADMIN','派单给 李国栋','2026-11-17 21:13:42'),(8,5,'提交',13,'REPORTER',NULL,'2026-11-16 19:23:42'),(9,5,'审核通过',2,'ADMIN',NULL,'2026-11-16 20:23:42'),(10,5,'派单',2,'ADMIN','派单给 孙志刚','2026-11-16 21:23:42'),(11,5,'接单',7,'REPAIRER',NULL,'2026-11-16 22:23:42'),(12,6,'提交',14,'REPORTER',NULL,'2026-11-17 13:23:42'),(13,6,'审核通过',3,'ADMIN',NULL,'2026-11-17 14:23:42'),(14,6,'派单',3,'ADMIN','派单给 张强','2026-11-17 15:23:42'),(15,6,'接单',4,'REPAIRER',NULL,'2026-11-17 16:23:42'),(16,7,'提交',11,'REPORTER',NULL,'2026-11-15 19:23:42'),(17,7,'审核通过',2,'ADMIN',NULL,'2026-11-15 20:23:42'),(18,7,'派单',2,'ADMIN','派单给 张强','2026-11-15 21:23:42'),(19,7,'接单',4,'REPAIRER',NULL,'2026-11-15 22:23:42'),(20,7,'完工',4,'REPAIRER','更换 LED 灯管与镇流器，测试正常发光','2026-11-15 23:23:42'),(21,8,'提交',12,'REPORTER',NULL,'2026-11-14 13:23:42'),(22,8,'审核通过',2,'ADMIN',NULL,'2026-11-14 14:23:42'),(23,8,'派单',2,'ADMIN','派单给 周建国','2026-11-14 15:23:42'),(24,8,'接单',8,'REPAIRER',NULL,'2026-11-14 16:23:42'),(25,8,'完工',8,'REPAIRER','检查为压缩机启动电容损坏，更换电容后运行正常','2026-11-14 17:23:42'),(26,8,'评价',12,'REPORTER','5 星评价','2026-11-14 18:23:42'),(27,9,'提交',13,'REPORTER',NULL,'2026-11-13 17:23:42'),(28,9,'审核通过',3,'ADMIN',NULL,'2026-11-13 18:23:42'),(29,9,'派单',3,'ADMIN','派单给 吴秀兰','2026-11-13 19:23:42'),(30,9,'接单',9,'REPAIRER',NULL,'2026-11-13 20:23:42'),(31,9,'完工',9,'REPAIRER','更换铰链并加固螺丝，柜门开合正常','2026-11-13 21:23:42'),(32,9,'评价',13,'REPORTER','4 星评价','2026-11-13 22:23:42'),(33,10,'提交',14,'REPORTER',NULL,'2026-11-17 15:23:42'),(34,10,'驳回',2,'ADMIN','照片无法看清故障位置，请补充清晰图片与具体位置','2026-11-17 16:23:42'),(35,11,'提交',10,'REPORTER',NULL,'2026-11-15 21:23:42'),(36,11,'取消',10,'REPORTER','报修人撤单','2026-11-15 22:23:42'),(37,12,'提交',11,'REPORTER',NULL,'2026-11-17 17:23:42');
/*!40000 ALTER TABLE `order_log` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `overdue_alert`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `overdue_alert` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警编号',
  `order_id` bigint NOT NULL COMMENT '所属工单',
  `stage` enum('ACCEPT','PROCESS') NOT NULL COMMENT '预警阶段：接单超时/处理超时',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '预警级别（连续超时升级，F8-3）',
  `handled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '已处理',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_handled` (`handled`),
  CONSTRAINT `fk_alert_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='超时预警表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `overdue_alert` WRITE;
/*!40000 ALTER TABLE `overdue_alert` DISABLE KEYS */;
/*!40000 ALTER TABLE `overdue_alert` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `part_usage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `part_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录编号',
  `order_id` bigint NOT NULL COMMENT '所属工单',
  `part_name` varchar(64) NOT NULL COMMENT '配件名称',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `fk_part_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配件消耗表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `part_usage` WRITE;
/*!40000 ALTER TABLE `part_usage` DISABLE KEYS */;
/*!40000 ALTER TABLE `part_usage` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `repair_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `repair_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单编号',
  `order_no` varchar(16) NOT NULL COMMENT '工单号（BX+日期+4位序号，如 BX202610120001）',
  `reporter_id` bigint NOT NULL COMMENT '报修人',
  `worker_id` bigint DEFAULT NULL COMMENT '承接维修工（派单后回填，可空）',
  `campus` varchar(32) NOT NULL COMMENT '校区枚举 code（报修必选）',
  `location_text` varchar(255) NOT NULL COMMENT '完整位置串：校区名 · 报修人自由填写的详细位置',
  `map_name` varchar(128) DEFAULT NULL COMMENT '地图选点 POI 名称/地址（补充定位，DB-02 v1.2）',
  `map_lng` decimal(10,6) DEFAULT NULL COMMENT '地图选点经度（DB-02 v1.2）',
  `map_lat` decimal(10,6) DEFAULT NULL COMMENT '地图选点纬度（DB-02 v1.2）',
  `fault_type` varchar(32) NOT NULL COMMENT '故障类型（字典：水电/木工门窗/网络多媒体/空调家电/电梯/实验设备/其他）',
  `urgency` enum('NORMAL','URGENT') NOT NULL DEFAULT 'NORMAL' COMMENT '紧急度（紧急单优先调度）',
  `description` varchar(1000) NOT NULL COMMENT '故障描述',
  `status` enum('PENDING_AUDIT','PENDING','PROCESSING','COMPLETED','EVALUATED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING_AUDIT' COMMENT '状态：待审核/待处理/处理中/已完成/已评价/已驳回/已取消（§5 状态机）',
  `is_overdue` tinyint(1) NOT NULL DEFAULT '0' COMMENT '超时标记位（非独立状态）',
  `main_order_id` bigint DEFAULT NULL COMMENT '关联主单（重复报修合并时指向主单，自关联）',
  `source_kb_id` bigint DEFAULT NULL COMMENT '从知识库「未解决去报修」进入时记录的文章 id（逻辑关联）',
  `ai_suggested_type` varchar(32) DEFAULT NULL COMMENT 'AI 建议的故障类型（未使用建议时为 NULL）',
  `ai_suggested_urgency` varchar(16) DEFAULT NULL COMMENT 'AI 建议的紧急度：NORMAL/URGENT',
  `ai_suggestion_used` tinyint(1) DEFAULT NULL COMMENT '用户是否点击采纳过 AI 建议',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号（并发接单/派单幂等）',
  `finish_summary` varchar(500) DEFAULT NULL COMMENT '完工维修说明（故障原因/处理方式）',
  `dispatch_source` enum('MANUAL','GRAB','AUTO') DEFAULT NULL COMMENT '最近一次派单来源（GRAB 仅历史数据）',
  `assign_expires_at` datetime DEFAULT NULL COMMENT '本次派单的接单截止时间（超时自动回收）',
  `due_at` datetime DEFAULT NULL COMMENT '处理截止时间（接单时按 SLA 写入）',
  `dispatch_count` int NOT NULL DEFAULT '0' COMMENT '累计派单次数（识别难派单）',
  `reject_count` int NOT NULL DEFAULT '0' COMMENT '累计被拒/超时回收次数',
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `audited_at` datetime DEFAULT NULL COMMENT '审核时间',
  `assigned_at` datetime DEFAULT NULL COMMENT '本次派单时间（回池时清空，历史见 order_log）',
  `accepted_at` datetime DEFAULT NULL COMMENT '接单时间（回池时清空）',
  `completed_at` datetime DEFAULT NULL COMMENT '完工时间',
  `rated_at` datetime DEFAULT NULL COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_status_submitted` (`status`,`submitted_at`),
  KEY `idx_worker_status` (`worker_id`,`status`),
  KEY `idx_worker_completed` (`worker_id`,`completed_at`),
  KEY `idx_pool` (`status`,`worker_id`),
  KEY `idx_offer_expire` (`status`,`assign_expires_at`),
  KEY `idx_due` (`status`,`due_at`),
  KEY `idx_reporter` (`reporter_id`),
  KEY `idx_campus` (`campus`),
  KEY `idx_campus_type_completed` (`campus`,`fault_type`,`completed_at`),
  KEY `fk_order_main` (`main_order_id`),
  CONSTRAINT `fk_order_main` FOREIGN KEY (`main_order_id`) REFERENCES `repair_order` (`id`),
  CONSTRAINT `fk_order_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_order_worker` FOREIGN KEY (`worker_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报修工单表（核心实体）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `repair_order` WRITE;
/*!40000 ALTER TABLE `repair_order` DISABLE KEYS */;
INSERT INTO `repair_order` VALUES (1,'BX202611170001',10,NULL,'UNIVERSITY_TOWN','大学城校区 · 1号宿舍楼 101',NULL,NULL,NULL,'空调家电','URGENT','宿舍空调不制冷，出风只有常温，晚上热得没法睡','PENDING_AUDIT',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-17 20:23:42',NULL,NULL,NULL,NULL,NULL),(2,'BX202611170002',11,NULL,'DONGFENG_ROAD','东风路校区 · 一教 201',NULL,NULL,NULL,'网络多媒体','NORMAL','一教201多媒体讲台无法开机，投影无信号','PENDING_AUDIT',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-17 18:23:42',NULL,NULL,NULL,NULL,NULL),(3,'BX202611170003',12,NULL,'LONGDONG','龙洞校区 · 3号宿舍楼 101',NULL,NULL,NULL,'水电','NORMAL','宿舍卫生间水龙头关不严，一直在滴水','PENDING',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-17 16:23:42','2026-11-17 16:53:42',NULL,NULL,NULL,NULL),(4,'BX202611170004',10,5,'UNIVERSITY_TOWN','大学城校区 · 一教 101',NULL,NULL,NULL,'水电','URGENT','一教101讲台插座打火冒烟，有安全隐患，请尽快处理','PENDING',0,NULL,NULL,NULL,NULL,NULL,0,NULL,'MANUAL','2026-11-17 21:43:42',NULL,1,0,'2026-11-17 19:23:42','2026-11-17 19:35:42','2026-11-17 21:13:42',NULL,NULL,NULL),(5,'BX202611160001',13,7,'UNIVERSITY_TOWN','大学城校区 · 1号宿舍楼 301',NULL,NULL,NULL,'木工门窗','NORMAL','宿舍阳台窗户合页断裂，窗扇无法固定，大风天有坠落风险','PROCESSING',0,NULL,NULL,NULL,NULL,NULL,0,NULL,'MANUAL','2026-11-16 23:23:42','2026-11-17 22:23:42',1,0,'2026-11-16 19:23:42','2026-11-16 20:23:42','2026-11-16 21:23:42','2026-11-16 22:23:42',NULL,NULL),(6,'BX202611170005',14,4,'UNIVERSITY_TOWN','大学城校区 · 一教 102',NULL,NULL,NULL,'其他','NORMAL','一教102门锁卡死，钥匙拧不动，影响上课','PROCESSING',0,NULL,NULL,NULL,NULL,NULL,0,NULL,'MANUAL','2026-11-17 17:23:42','2026-11-18 16:23:42',1,0,'2026-11-17 13:23:42','2026-11-17 14:23:42','2026-11-17 15:23:42','2026-11-17 16:23:42',NULL,NULL),(7,'BX202611150001',11,4,'UNIVERSITY_TOWN','大学城校区 · 1号宿舍楼 102',NULL,NULL,NULL,'水电','NORMAL','宿舍灯管闪烁，疑似镇流器故障需要更换','COMPLETED',0,NULL,NULL,NULL,NULL,NULL,0,'更换 LED 灯管与镇流器，测试正常发光','MANUAL','2026-11-15 23:23:42','2026-11-16 22:23:42',1,0,'2026-11-15 19:23:42','2026-11-15 20:23:42','2026-11-15 21:23:42','2026-11-15 22:23:42','2026-11-15 23:23:42',NULL),(8,'BX202611140001',12,8,'UNIVERSITY_TOWN','大学城校区 · 二教 101',NULL,NULL,NULL,'空调家电','NORMAL','二教101柜机启动后自动跳闸，无法制冷','EVALUATED',0,NULL,NULL,NULL,NULL,NULL,0,'检查为压缩机启动电容损坏，更换电容后运行正常','MANUAL','2026-11-14 17:23:42','2026-11-15 16:23:42',1,0,'2026-11-14 13:23:42','2026-11-14 14:23:42','2026-11-14 15:23:42','2026-11-14 16:23:42','2026-11-14 17:23:42','2026-11-14 18:23:42'),(9,'BX202611130001',13,9,'LONGDONG','龙洞校区 · 3号宿舍楼 202',NULL,NULL,NULL,'木工门窗','NORMAL','宿舍衣柜柜门脱落，铰链螺丝滑丝','EVALUATED',0,NULL,NULL,NULL,NULL,NULL,0,'更换铰链并加固螺丝，柜门开合正常','MANUAL','2026-11-13 21:23:42','2026-11-14 20:23:42',1,0,'2026-11-13 17:23:42','2026-11-13 18:23:42','2026-11-13 19:23:42','2026-11-13 20:23:42','2026-11-13 21:23:42','2026-11-13 22:23:42'),(10,'BX202611170006',14,NULL,'SHAHE','沙河校区 · 体育馆 北侧卫生间',NULL,NULL,NULL,'水电','NORMAL','卫生间顶灯不亮了','REJECTED',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-17 15:23:42','2026-11-17 16:23:42',NULL,NULL,NULL,NULL),(11,'BX202611150002',10,NULL,'PANYU','番禺校区 · 图书馆 电梯厅',NULL,NULL,NULL,'电梯','NORMAL','图书馆电梯按钮失灵（后发现是自己看错楼层）','CANCELLED',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-15 21:23:42',NULL,NULL,NULL,NULL,NULL),(12,'BX202611170007',11,NULL,'JIEYANG','揭阳校区 · 教学楼 A 栋 301',NULL,NULL,NULL,'水电','NORMAL','教学楼三层走廊照明跳闸，需排查配电箱','PENDING_AUDIT',0,NULL,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,0,0,'2026-11-17 17:23:42',NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `repair_order` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `revisit_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `revisit_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  `order_id` bigint NOT NULL COMMENT '关联工单',
  `worker_id` bigint DEFAULT NULL COMMENT '被评价维修工',
  `rating` int NOT NULL COMMENT '本次评价星级（1-5）',
  `status` enum('PENDING','HANDLED','CLOSED') NOT NULL DEFAULT 'PENDING' COMMENT '待回访/已回访/已关闭',
  `handled_by` bigint DEFAULT NULL COMMENT '处理管理员',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `remark` varchar(300) DEFAULT NULL COMMENT '回访说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revisit_order` (`order_id`),
  KEY `idx_revisit_status` (`status`,`created_at`),
  KEY `fk_revisit_worker` (`worker_id`),
  KEY `fk_revisit_admin` (`handled_by`),
  CONSTRAINT `fk_revisit_admin` FOREIGN KEY (`handled_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_revisit_order` FOREIGN KEY (`order_id`) REFERENCES `repair_order` (`id`),
  CONSTRAINT `fk_revisit_worker` FOREIGN KEY (`worker_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='差评回访任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `revisit_task` WRITE;
/*!40000 ALTER TABLE `revisit_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `revisit_task` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `skill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能编号',
  `name` varchar(32) NOT NULL COMMENT '技能名称（水电/木工门窗/网络多媒体/空调家电/电梯/实验设备...）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用状态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能字典表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `skill` WRITE;
/*!40000 ALTER TABLE `skill` DISABLE KEYS */;
INSERT INTO `skill` VALUES (1,'水电',1),(2,'木工门窗',1),(3,'网络多媒体',1),(4,'空调家电',1),(5,'电梯',1),(6,'实验设备',1),(7,'其他',1);
/*!40000 ALTER TABLE `skill` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号',
  `job_no` varchar(32) NOT NULL COMMENT '学工号/工号（登录名）',
  `name` varchar(64) NOT NULL COMMENT '姓名',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希（BCrypt）',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `role` enum('REPORTER','ADMIN','REPAIRER','SYS_ADMIN') NOT NULL COMMENT '角色：报修人/后勤管理员/维修工/系统管理员',
  `department` varchar(128) DEFAULT NULL COMMENT '部门（教职工）',
  `address` varchar(128) DEFAULT NULL COMMENT '住址（学生：宿舍楼栋·房间，与部门二选一填写）',
  `campus` varchar(32) DEFAULT NULL COMMENT '所属校区（Campus code，报修人资料）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 2停用 3注销',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_no` (`job_no`),
  KEY `idx_role_status` (`role`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表（统一用户+角色区分）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'sa','系统管理员','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000001','SYS_ADMIN',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(2,'admin1','李老师','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000002','ADMIN','后勤处',NULL,NULL,1,'2026-11-17 21:23:42'),(3,'admin2','王科长','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000003','ADMIN','后勤处',NULL,NULL,1,'2026-11-17 21:23:42'),(4,'worker01','张强','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000101','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(5,'worker02','李国栋','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000102','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(6,'worker03','赵敏','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000103','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(7,'worker04','孙志刚','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000104','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(8,'worker05','周建国','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000105','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(9,'worker06','吴秀兰','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000106','REPAIRER',NULL,NULL,NULL,1,'2026-11-17 21:23:42'),(10,'student01','陈明','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000201','REPORTER',NULL,'1号宿舍楼101','UNIVERSITY_TOWN',1,'2026-11-17 21:23:42'),(11,'student02','刘婷','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000202','REPORTER',NULL,'2号宿舍楼301','UNIVERSITY_TOWN',1,'2026-11-17 21:23:42'),(12,'student03','王磊','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000203','REPORTER',NULL,'2号宿舍楼101','LONGDONG',1,'2026-11-17 21:23:42'),(13,'student04','赵雪','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000204','REPORTER',NULL,'1号宿舍楼301','UNIVERSITY_TOWN',1,'2026-11-17 21:23:42'),(14,'student05','钱芳','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000205','REPORTER',NULL,'1号宿舍楼302','UNIVERSITY_TOWN',1,'2026-11-17 21:23:42'),(15,'teacher01','孙老师','$2a$10$CfTVwS5kCpMDLrSkBVBsM.tyihB4KUgWi0.bBjJ0xjBO.pz6dLLTa','13900000206','REPORTER','计算机学院',NULL,'UNIVERSITY_TOWN',1,'2026-11-17 21:23:42');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `worker_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `worker_profile` (
  `user_id` bigint NOT NULL COMMENT '用户编号（=维修工）',
  `on_duty` tinyint(1) NOT NULL DEFAULT '1' COMMENT '在岗状态',
  `load_limit` int NOT NULL DEFAULT '5' COMMENT '负载上限（在办工单数）',
  `score` decimal(3,2) NOT NULL DEFAULT '5.00' COMMENT '综合评分（1.00~5.00）',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_worker_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修工档案表（当前负载不落库，实时 COUNT）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `worker_profile` WRITE;
/*!40000 ALTER TABLE `worker_profile` DISABLE KEYS */;
INSERT INTO `worker_profile` VALUES (4,1,5,5.00),(5,1,5,5.00),(6,1,5,5.00),(7,1,5,5.00),(8,1,5,5.00),(9,1,5,4.00);
/*!40000 ALTER TABLE `worker_profile` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `worker_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `worker_scope` (
  `user_id` bigint NOT NULL COMMENT '维修工',
  `campus` varchar(32) NOT NULL COMMENT '校区枚举 code（六校区，见 Campus）',
  PRIMARY KEY (`user_id`,`campus`),
  KEY `idx_campus` (`campus`),
  CONSTRAINT `fk_scope_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修工负责校区表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `worker_scope` WRITE;
/*!40000 ALTER TABLE `worker_scope` DISABLE KEYS */;
INSERT INTO `worker_scope` VALUES (6,'DONGFENG_ROAD'),(6,'LONGDONG'),(9,'LONGDONG'),(9,'PANYU'),(8,'SHAHE'),(4,'UNIVERSITY_TOWN'),(5,'UNIVERSITY_TOWN'),(7,'UNIVERSITY_TOWN'),(8,'UNIVERSITY_TOWN');
/*!40000 ALTER TABLE `worker_scope` ENABLE KEYS */;
UNLOCK TABLES;
DROP TABLE IF EXISTS `worker_skill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `worker_skill` (
  `user_id` bigint NOT NULL COMMENT '维修工',
  `skill_id` bigint NOT NULL COMMENT '技能',
  `level` enum('PRIMARY','SECONDARY') NOT NULL DEFAULT 'PRIMARY' COMMENT '主修/兼修（派单评分因子）',
  PRIMARY KEY (`user_id`,`skill_id`),
  KEY `idx_skill` (`skill_id`),
  CONSTRAINT `fk_ws_skill` FOREIGN KEY (`skill_id`) REFERENCES `skill` (`id`),
  CONSTRAINT `fk_ws_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修工-技能关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `worker_skill` WRITE;
/*!40000 ALTER TABLE `worker_skill` DISABLE KEYS */;
INSERT INTO `worker_skill` VALUES (4,1,'PRIMARY'),(4,4,'SECONDARY'),(5,1,'PRIMARY'),(6,3,'PRIMARY'),(6,6,'SECONDARY'),(7,2,'PRIMARY'),(8,4,'PRIMARY'),(8,5,'SECONDARY'),(9,1,'PRIMARY'),(9,2,'SECONDARY');
/*!40000 ALTER TABLE `worker_skill` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

