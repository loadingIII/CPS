# CPS · 校园报修与维修工单管理系统（R1 · MVP 核心闭环）

依据 [第一阶段计划书](../../第一阶段计划书.md)（AI 执行版）实现的三端可运行系统。

## 目录结构

```
CPS/
├── backend/        后端：Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + JWT + Knife4j
├── admin-web/      管理端：Vue 3 + Vite + Element Plus + Pinia
├── miniprogram/    小程序：微信原生（师生端 + 维修工端，按角色渲染）
├── scripts/        冒烟测试与并发测试脚本（Node 24+，无第三方依赖）
└── docs/           测试记录、演示脚本
```

## 启动步骤

### 0. 数据库
MySQL 8 本机 root/123321；建库脚本 `design/schema.sql`（v2.1，含派单调度字段），
后端首次启动会自动检测空库并写入种子数据（7 类技能、15 个账号、12 张演示工单）。

已有旧库升级：执行 `design/migration_v2.1_dispatch.sql`（新增 `dispatch_source / grab_open_at /
assign_expires_at / due_at / dispatch_count / reject_count / finish_summary` 与 `worker_skill.level`，
并回填存量数据），无需重建。

### 1. 后端（端口 8080）
```bash
cd CPS/backend
mvn spring-boot:run        # 或 mvn package 后 java -jar target/repair-backend-1.0.0.jar
```
- 接口文档：http://localhost:8080/doc.html
- 单元测试：`mvn test`（状态机 14 个用例）

### 2. 管理端（端口 5173）
```bash
cd CPS/admin-web
npm install     # 首次
npm run dev     # 开发模式，已代理 /api 与 /uploads 到 8080
```
登录：admin1 / 123456（后勤管理员）或 sa / 123456（系统管理员）。

### 3. 小程序
微信开发者工具导入 `CPS/miniprogram`，appid 用测试号（touristappid），
「详情 → 本地设置」勾选**不校验合法域名**；登录 student01 / 123456（师生）或 worker01 / 123456（维修工）。
真机预览时把 `utils/request.js` 的 `BASE` 改为电脑局域网 IP。

### 4. 自动化验证
```bash
cd CPS/scripts
node smoke.mjs                # 32 项接口冒烟（全流程闭环）
node concurrent-accept.mjs    # 并发接单幂等（5 并发 → 1 成功 4 冲突）
node grab-flow.mjs            # 抢单：资格过滤 / 开放时间 / 抢到即接单（16 项）
node dispatch-flow.mjs        # 派单：队列拆分 / 负载上限 / 派单与抢单竞争（13 项）
node recycle-flow.mjs         # 接单超时自动回收（5 项，需等待定时任务一个周期）
```

## 派单与抢单规则（v2.1）

| 场景 | 规则 |
|------|------|
| 待处理阶段 | 派单子状态派生：`POOL`（待派单，`worker_id` 为空）/ `OFFERED`（已派待接）/ `TAKEN`（已承接） |
| 定向派单 | 管理员按推荐分（0.40 技能 + 0.25 负载 + 0.20 按时率 + 0.15 评分）派单；改派必须填理由 |
| 抢单开放 | 紧急单审核即开放；普通单 20 分钟后开放；拒单/转单回流立即开放；管理员可手动开放 |
| 抢单 | 先到先得，**抢到即接单**，直接进入处理中 |
| 容量 | 派单/抢单先锁 `worker_profile` 容量行再统计在办数，负载上限不可突破 |
| 超时 | 接单超时自动回收（紧急 30 分 / 普通 120 分）；处理超时打 `is_overdue` 标记 |
| 完工 | 维修说明落库 `finish_summary`；完工 7 天未评价自动默认好评 |
| 冷却 | 拒单/被超时回收后，同一师傅 24 小时内不再被推荐该单 |

参数都在 `backend/src/main/resources/application.yml` 的 `dispatch.*` 下，可直接改配置演示。

## 演示
见 [docs/R1演示脚本.md](docs/R1演示脚本.md)（「一个工单的一生」）；测试记录见 [docs/R1测试记录.md](docs/R1测试记录.md)。
调度重构的完整设计见 [docs/派单与抢单调度设计方案.md](docs/派单与抢单调度设计方案.md)。

## 已知偏差
- 本机无 Redis：工单号采用数据库 max+1 + 唯一键重试，clientToken 幂等用进程内缓存（计划书 §2.3 降级策略，单实例等价）。
- 定时任务（超时回收 / 默认好评）为单实例 `@Scheduled`，多实例部署需改为分布式调度或加数据库锁。
