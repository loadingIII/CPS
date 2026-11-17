# 数据库脚本

本目录是**可运行项目内的数据库脚本**，与 `../../design/数据库设计.md` 配套。
`../../design/` 只保留设计文档，不再放 SQL。

## 文件

| 文件 | 用途 |
|---|---|
| `init.sql` | **最终一键脚本**：建库 + 15 张表 + 演示数据（15 个账号 / 7 类技能 / 12 张工单）。会 `DROP` 重建 `campus_repair`，执行前备份 |
| `schema.sql` | 全量建库脚本（MySQL 8.0 / InnoDB / utf8mb4），包含 15 张表、索引、外键；**不含种子数据**，启动后端由 `SeedRunner` 写入演示数据 |
| `migration_v2.1_dispatch.sql` | R2 派单调度字段（`dispatch_source / assign_expires_at / due_at / dispatch_count / reject_count / finish_summary` 等） |
| `migration_v2.2_campus.sql` | 六校区 code 迁移（历史 EAST/WEST/SOUTH → 新 code，并改写 `location_text` 前缀） |
| `migration_v2.3_r2.sql` | `notification.biz_id`、`revisit_task` 表、`idx_worker_completed` |
| `migration_v2.4_profile.sql` | `sys_user.campus`（报修人所属校区） |
| `migration_v2.5_kb_simplify.sql` | 知识库去掉 `fault_type`，改为 Markdown 上传即入库 |
| `migration_v2.6_remove_grab.sql` | 移除抢单：删除 `grab_open_at`，重建 `idx_pool(status, worker_id)` |
| `migration_v3.0_r3.sql` | R3：AI 建议字段、知识库来源字段、`kb_event` 表、`kb_article` 草稿状态；可重复执行 |

## 全新安装

### 方式 A：一键脚本（推荐，带演示数据）

```bash
# 在 CPS/ 目录下执行；会 DROP 并重建 campus_repair 库
mysql --default-character-set=utf8mb4 -uroot -p < db/init.sql
```

导入后直接启动后端即可，15 个账号、7 类技能、12 张演示工单已就绪，不需要等 `SeedRunner`。
账号密码统一 `123456`。

### 方式 B：只建空库（数据由 SeedRunner 生成）

```bash
mysql --default-character-set=utf8mb4 -uroot -p < db/schema.sql
```

然后启动后端，`SeedRunner` 会自动写入种子数据（7 类技能、15 个账号、12 张演示工单）。
`schema.sql` 使用 `CREATE DATABASE IF NOT EXISTS`，但表是 `CREATE TABLE`（非 IF NOT EXISTS）；
如果 `campus_repair` 已存在，先重建：

```bash
mysql -uroot -p -e "DROP DATABASE IF EXISTS campus_repair;"
mysql --default-character-set=utf8mb4 -uroot -p < db/schema.sql
```

## 已有旧库升级

按版本顺序执行，**不要重复执行已执行过的迁移**（`v3.0_r3` 除外，它做了 information_schema 判断，可重复）：

```bash
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.1_dispatch.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.2_campus.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.3_r2.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.4_profile.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.5_kb_simplify.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v2.6_remove_grab.sql
mysql --default-character-set=utf8mb4 -uroot -p < db/migration_v3.0_r3.sql
```

> `migration_v2.6_remove_grab.sql` 执行前先停后端，否则旧进程仍在写 `grab_open_at` 会报 Unknown column。
> `migration_v3.0_r3.sql` 执行后重启后端，JVM 不会热加载实体字段。

## 连接参数

默认与 `backend/.env` 一致：`localhost:3306`、库名 `campus_repair`、用户 `root`、密码 `123321`。
如不同，改 `.env` 的 `DB_URL / DB_USERNAME / DB_PASSWORD`，或执行时加 `-h / -P / -u`。
