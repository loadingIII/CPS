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
MySQL 8 本机 root/123321；建库脚本 `design/schema.sql`（已含 DB-01 parent_id 调整），
后端首次启动会自动检测空库并写入种子数据（14 个位置节点、7 类技能、15 个账号、12 张演示工单）。

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
```

## 演示
见 [docs/R1演示脚本.md](docs/R1演示脚本.md)（「一个工单的一生」）；测试记录见 [docs/R1测试记录.md](docs/R1测试记录.md)。

## 已知偏差
- 本机无 Redis：工单号采用数据库 max+1 + 唯一键重试，clientToken 幂等用进程内缓存（计划书 §2.3 降级策略，单实例等价）。
