# CPS · 校园报修与维修工单管理系统（R2 · 效率增强）

依据 [第一阶段计划书](../../第一阶段计划书.md)（R1 闭环）与 [第二阶段计划书](../../第二阶段计划书.md)（R2 效率增强）实现的三端可运行系统。

## 目录结构

```
CPS/
├── backend/        后端：Java 17 + Spring Boot 3.4 + Spring AI + MyBatis-Plus + MySQL 8 + JWT
├── admin-web/      管理端：Vue 3 + Vite + Element Plus + Pinia
├── miniprogram/    小程序：微信原生（师生端 + 维修工端，按角色渲染）
├── scripts/        联调脚本（Node）与知识库入库脚本 ingest_kb.py
├── chroma/         本地 ChromaDB 实例（start.ps1 + data/）
└── docs/           测试记录、演示脚本
```

## 启动步骤

### 0. 数据库
MySQL 8 本机 root/123321；建库脚本 `design/schema.sql`（v2.6，含派单调度字段、已移除抢单），
后端首次启动会自动检测空库并写入种子数据（7 类技能、15 个账号、12 张演示工单）。

已有旧库升级：执行 `design/migration_v2.1_dispatch.sql`（新增 `dispatch_source / grab_open_at /
assign_expires_at / due_at / dispatch_count / reject_count / finish_summary` 与 `worker_skill.level`，
并回填存量数据），无需重建。

R2 升级：再执行 `design/migration_v2.3_r2.sql`（`notification.biz_id`、`revisit_task` 表、
`idx_worker_completed` 索引）。知识库不再内置示例文章，改为管理端上传 Markdown 导入。

个人资料升级：执行 `design/migration_v2.4_profile.sql`（`sys_user.campus`，报修人所属校区）。

抢单移除（v2.6）：执行 `design/migration_v2.6_remove_grab.sql`（删除 `grab_open_at` 与旧 `idx_pool`，
重建 `idx_pool(status, worker_id)`）。**执行前先停后端**，否则旧进程写 `grab_open_at` 会报 Unknown column。

### 1. 后端（端口 8080）
```bash
cd backend
cp .env.example .env       # 首次：填数据库密码与（可选的）AI 配置
mvn spring-boot:run        # 或 mvn package 后 java -jar target/repair-backend-1.0.0.jar
```
- 配置：数据库、JWT、AI、Chroma、RAG 参数统一在 `backend/.env`，键名与 `.env.example` 一致
- 接口文档：http://localhost:8080/doc.html
- 单元测试：`mvn test`（状态机 15 个用例）
- 依赖升级：Spring Boot 3.2.5 → 3.4.5（Spring AI 1.1.8 基线）；`pom.xml` 已回退编译/测试插件版本，兼容本机 Maven 3.6.1

### 2. 管理端（端口 5173）
```bash
cd admin-web
npm install     # 首次
npm run dev     # 开发模式，已代理 /api 与 /uploads 到 8080
```
登录：admin1 / 123456（后勤管理员）或 sa / 123456（系统管理员）。

### 3. 小程序
微信开发者工具导入 `CPS/miniprogram`，appid 用测试号（touristappid），
「详情 → 本地设置」勾选**不校验合法域名**；登录 student01 / 123456（师生）或 worker01 / 123456（维修工）。
真机预览时把 `utils/request.js` 的 `BASE` 改为电脑局域网 IP。

> 项目运行中新增的 js 文件（如 `utils/sse.js`）若报 `module 'xxx' is not defined`，
> 是开发者工具的文件索引没刷新：先「编译」，仍报错就「工具 → 清缓存 → 全部清除」再编译。
> `project.private.config.json` 里的 `ignoreDevUnusedFiles` 已置为 false，避免新文件被当成未使用文件排除。

### 4. 自动化验证
```bash
cd CPS/scripts
node smoke.mjs                # 32 项接口冒烟（全流程闭环）
node concurrent-accept.mjs    # 并发接单幂等（5 并发 → 1 成功 4 冲突）
node dispatch-flow.mjs        # 派单：队列拆分 / 负载上限 / 未派单不可见（9 项）
node recycle-flow.mjs         # 接单超时自动回收（5 项，需等待定时任务一个周期）
node r2-flow.mjs              # R2 增量：知识库 / 按时率 / 通知 / 催单限频 / 配件权限 / 批量审核 / 重报改字段 / 完工带配件 / 差评回访（32 项）
```

## 智能助手（Spring AI + Chroma）

基于 Spring AI 的 RAG 问答：模型只暴露一个 `searchKnowledgeBase` 工具，先检索 Chroma 再回答，
小程序通过 SSE 逐字渲染。

### 1. 配置

编辑 `backend/.env`（键名与 `.env.example` 一致，示例文件不含真实密钥）：

```bash
AGENT_ENABLED=true
AI_BASE_URL=https://api.deepseek.com      # 对话端点（任意 OpenAI 兼容）
AI_API_KEY=sk-xxx
AI_CHAT_MODEL=deepseek-chat
# 向量模型可单独一套端点/密钥；留空则沿用上面的对话配置
AI_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_EMBEDDING_API_KEY=sk-yyy
AI_EMBEDDING_MODEL=qwen3.7-text-embedding
AI_EMBEDDING_DIMENSIONS=1024        # 仅用于入库校验与日志，不传给模型
CHROMA_HOST=localhost
CHROMA_PORT=8000
CHROMA_COLLECTION=campus_repair_kb
```

> 对话模型不带 embedding 能力时（如 DeepSeek），必须单独配 `AI_EMBEDDING_*`。
> 一旦填了 `AI_EMBEDDING_BASE_URL`，就不会再拿对话密钥去试——两家服务商的 key 不通用，
> 漏填时 `GET /api/agent/health` 会直接给出 `configIssue` 提示。

未填 `AI_API_KEY`、或单独配了向量端点却漏填 `AI_EMBEDDING_API_KEY` 时，不会装配向量问答，
自动回落到关键词问答（MySQL 知识库），所以没有密钥也能正常启动和演示。

### 2. 启动 Chroma

本地实例已放在 `CPS/chroma/`，数据落在 `CPS/chroma/data/`：

```powershell
cd CPS\chroma
.\start.ps1                 # 默认 8000 端口，Ctrl+C 停止
.\start.ps1 -Port 8001      # 换端口时记得同步改 backend/.env 的 CHROMA_PORT
```

首次需要 `pip install "chromadb>=1.0"`；也可以用 Docker：

```bash
docker run -d --name chroma -p 8000:8000 -v ${PWD}/chroma/data:/chroma/chroma chromadb/chroma:latest
```

- 需要 Chroma **≥ 1.0**（Spring AI 1.1 按集合 UUID 查询，0.x 只认集合名会 400）
- 已验证组合：Python 客户端 1.5.9 + Chroma 1.5.9
- 自检：`curl http://localhost:8000/api/v2/heartbeat`
- 详细操作（停止、重置、常见问题）见 `CPS/chroma/README.md`

### 3. 文档入库

```bash
cd CPS/scripts
python ingest_kb.py --path ../docs/kb --dry-run     # 先看切分结果，不联网
python ingest_kb.py --path ../docs/kb --reset       # 正式入库（先清空旧块）
python ingest_kb.py --path ../knowledge_md --exclude README.md --reset   # 排除说明文件
```

- `--reset`：清空集合内的块、**保留集合本身**，集合 UUID 不变，后端不用重启
- `--recreate`：删除并重建集合，用于换 embedding 模型/维度；**执行后需重启后端**

- 支持单个 `.md` 或整个目录（递归），可重复 `--path`；`--exclude` 按文件名/通配排除
- 可选 front-matter：`title` / `fault_type`（仅影响写入 Chroma 的元数据，管理端上传不读它）
- 切分：按标题分节后做**滑动窗口**（默认 500 字 / 重叠 80，尽量在段落或句号处断开）
- 写入：`collection.delete(where doc_id)` 后 upsert，重复执行不会产生重复块
- `--mock-embedding`：无密钥时用占位向量验证写库链路（不可检索）
- 依赖：`pip install chromadb`

常见报错都有直白提示：连不上 Chroma 会给出启动命令；向量接口 4xx 会回显平台原文并提示检查
`AI_EMBEDDING_MODEL` / `AI_EMBEDDING_API_KEY`；换模型导致集合维度冲突会提示加 `--reset`。

### 4. 自检与接口

| 接口 | 说明 |
|---|---|
| `POST /api/agent/chat/stream` | SSE 流式问答：`start → refs → delta… → done`，异常发 `error` |
| `POST /api/agent/chat` | 同步问答（低版本基础库兜底） |
| `GET /api/agent/health?deep=true` | 管理员自检：模式、`configIssue`（缺哪项配置）、Chroma 连通性、集合条数、向量维度与端点来源 |

向量库管理（管理员，供后续文档管理复用）：

| 接口 | 说明 |
|---|---|
| `GET /api/agent/chroma/stats` | 集合名、UUID、条数 |
| `GET /api/agent/chroma/chunks?limit=&offset=` | 分页浏览块（不返回向量） |
| `POST /api/agent/chroma/chunks` | 批量写入/覆盖，自动向量化，body 为 `[{id,text,metadata}]` |
| `DELETE /api/agent/chroma/chunks/{id}` | 按块 id 删除 |
| `DELETE /api/agent/chroma/docs/{docId}` | 按 `metadata.doc_id` 删除整篇 |
| `POST /api/agent/chroma/clear` | 清空所有块（仅 SYS_ADMIN；保留集合，无需重启后端） |

```bash
curl -N -X POST http://localhost:8080/api/agent/chat/stream \
  -H "Content-Type: application/json" -H "Accept: text/event-stream" \
  -H "Authorization: Bearer <token>" -d '{"message":"空调不制冷"}'
```

### 5. 管理端知识库同步

管理端「维修知识库」的增删改会实时同步到 Chroma。**新增文章 = 直接上传 `.md`**：
点「上传 Markdown」选文件后，后端去掉 front-matter、按文件名生成标题、切分、向量化并落库，
一步完成，不再需要手工填正文。

- 标题 = 文件名（去 `.md` 后缀），front-matter 的 title 与一级标题都不参与命名
- 知识库不再按故障类型分类，`kb_article` 也没有 `fault_type` 字段
- 只收 `.md` / `.markdown`，≤1MB，UTF-8
- 返回 `{id,title,chunks,indexed,warning}`；文章已存但索引失败时 `warning` 给出原因
- 管理端只能「查看」（渲染 Markdown）、改标题、启停与删除，不能编辑正文

| 操作 | 向量库行为 |
|---|---|
| 上传 Markdown | 解析 → 建文章 → 切片 → 向量化 → 写入，一次完成 |
| 编辑 | 先按 `kb_id` 删旧块，再按 500/80 滑动窗口切片写入 |
| 停用 / 内容清空 | 删除该文章的全部块 |
| 删除文章 | 删除该文章的全部块 |
| 点「重建向量索引」 | 全量重建，返回 `{articles,indexed,chunks,failed}` |
| 后端启动 | 集合为空且库里有启用文章时，自动全量同步一次 |

每个块的元数据：

```json
{
  "kb_id": 1, "doc_id": "kb:1", "source": "kb_article",
  "title": "空调不制冷先查这三处",
  "section": "1. 检查遥控器", "chunk_index": 0,
  "char_start": 0, "char_end": 120, "updated_at": "2026-11-14T22:26:29"
}
```

`kb_id` 让检索结果直接带回文章 id，小程序点引用卡片可跳到详情；
md 文件入库的块没有 `kb_id`，会回退到按标题匹配。

小程序知识库详情会把 Markdown 渲染成富文本（标题、段落、粗体/斜体、行内代码、代码块、引用、
有序/无序列表、表格、分割线、链接、图片），解析失败时回退为纯文本展示。

接口：`POST /api/kb/articles/import`（multipart，字段名 `file`）、
`POST /api/kb/articles/reindex`、`DELETE /api/kb/articles/{id}`。

Chroma 不可用时文章本身的增删改不受影响，只记一条 WARN 日志；恢复后点「重建向量索引」补齐。

## R2 增量能力（第二阶段）

| 能力 | 入口 | 说明 |
|------|------|------|
| 推荐派单 | 派单工作台 | 合格候选按匹配分排序，首位标「推荐」，一键采纳；改派仍需理由 |
| 超时预警 | 管理端「超时预警」 | 接单超时、处理超时均写 `overdue_alert` 并通知管理员与相关人；处理超时的工单同时自动回收至待派单池，同一单重复超时按级别升级 |
| 站内通知 | 顶栏铃铛 / 小程序「消息中心」 | 审核、派单、接单、拒单、完工、评价、催单、预警、回访、合并共 10 类事件 |
| 催单 | 报修人端工单详情 | 2 小时内限 1 次、每日最多 3 次，同时通知维修工与管理员 |
| 评价标签 / 差评回访 | 小程序评价页 / 管理端「差评回访」 | 4 个固定标签；≤2 星自动生成回访任务，状态为待回访/已回访/已关闭 |
| 重复报修 | 报修提交前 | 同校区 + 同类型 + 48 小时 + 位置相似，提示并可直接查看已有单；管理员可合并（副单作废并关联主单） |
| 批量审核 | 审核工作台 | 多选后批量通过/驳回，逐条返回结果，单条失败不影响其他 |
| 知识库 | 小程序「维修知识库」/ 管理端 | 关键词搜索、详情浏览量；管理端上传 Markdown 即切片入库，只能查看/改标题/启停/删除 |
| 配件登记 | 小程序完工上报页 | 承接维修工在完工前登记配件名称与数量 |
| 统计绩效 | 工作台 | 近 14 日按时完成率、近 30 日维修工绩效排行（完工 <5 单标注样本不足） |
| 智能助手 | 小程序首页底部悬浮窗 | Spring AI + Chroma 的 RAG 问答：只暴露一个知识库检索工具，SSE 流式逐字输出；默认半屏悬浮在 TabBar 上方，上滑展开全屏、下滑回半屏；右上角可收起为悬浮球；识别故障类型、命中教程可查看、一键带类型去报修；未配密钥时回落关键词问答 |

## 账号与注册

| 角色 | 获取方式 | 登录入口 |
|------|----------|----------|
| 报修人（师生） | 小程序登录页「师生立即注册」自助注册，角色固定为报修人，注册成功自动登录 | 小程序，选择「我是报修人」 |
| 维修工 | 管理端「维修工管理」由管理员开通 | 小程序，选择「我是维修工」 |
| 后勤/系统管理员 | 系统管理员在「用户管理」开通 | 管理端登录页（仅接受 ADMIN / SYS_ADMIN） |

- 注册校验：学工号 4-20 位字母/数字/下划线/短横线、姓名 2-20 字、手机号 11 位、密码 6-32 位、二次确认；学工号唯一键兜底重复注册。
- 登录入口选错会直接提示「该账号不是维修工账号，请切换登录入口」，不会登录后再遇到 403。
- 接口：`POST /api/auth/register`（公开，无需登录）、`POST /api/auth/login`（`role` 可选，管理端不传）。
- 课程演示环境未接短信验证码与校园统一身份认证；生产环境应补上，避免任意学工号注册。

### 个人中心

- 资料以只读方式展示，点「编辑资料」进入独立编辑页，保存后返回自动刷新。
- 报修人：所属校区（必填，报修时作为默认校区）、学院/部门、常用地址、联系电话。
- 维修工：服务校区与技能由后勤统一维护、只读展示；仅联系电话可改；不展示维修知识库入口。
- 维修工首页：顶部身份卡展示在岗状态、工号、综合评分、技能与在办负载（`/workers/me`）；中间是「待接单 / 维修中 / 已完成」三栏概览；下方按「加急优先 + 先来先做」列出最多 3 张待处理工单，带接单/处理剩余时间并每分钟重算；底部是工单地图与消息通知入口（铃铛角标显示未读数，数据取自 `/notifications/unread-count`）。

## 派单规则（v2.6：仅管理端派单）

> v2.6 起移除抢单大厅、`/grab-pool`、`/claim`、`/broadcast` 与抢单开放时间，维修工只能接收管理员派单。

| 场景 | 规则 |
|------|------|
| 待处理阶段 | 派单子状态派生：`POOL`（待派单，`worker_id` 为空）/ `OFFERED`（已派待接）/ `TAKEN`（已承接） |
| 定向派单 | 管理员按推荐分（0.40 技能 + 0.25 负载 + 0.20 按时率 + 0.15 评分）派单；改派必须填理由 |
| 派单后 | 工单进入 `OFFERED`，维修工在「待接单」确认接单，超时未接自动回收 |
| 容量 | 派单先锁 `worker_profile` 容量行再统计在办数，负载上限不可突破 |
| 拒绝/转单 | 维修工拒单或转单后清空承接信息，回到待派单池，由管理员重新派单 |
| 超时 | 接单超时自动回收（紧急 30 分 / 普通 120 分）；处理超时（紧急 4h / 普通 24h）自动回收至待派单池、清空承接人并保留超时标记，由管理员重新派单 |
| 完工 | 维修说明落库 `finish_summary`；完工 7 天未评价自动默认好评 |
| 冷却 | 拒单/被超时回收后，同一师傅 24 小时内不再被推荐该单（处理超时回收同样计入冷却） |

参数都在 `backend/src/main/resources/application.yml` 的 `dispatch.*` 下，可直接改配置演示。

## 演示
见 [docs/R1演示脚本.md](docs/R1演示脚本.md)（「一个工单的一生」）；测试记录见 [docs/R1测试记录.md](docs/R1测试记录.md)。
派单模型与评分设计见 [docs/派单与抢单调度设计方案.md](docs/派单与抢单调度设计方案.md)
（其中抢单部分为 v2.1 的历史设计记录，v2.6 已移除）。

## 已知偏差
- 本机无 Redis：工单号采用数据库 max+1 + 唯一键重试，clientToken 幂等用进程内缓存（计划书 §2.3 降级策略，单实例等价）。
- 定时任务（超时回收 / 默认好评）为单实例 `@Scheduled`，多实例部署需改为分布式调度或加数据库锁。
- 通知为站内消息 + 页面轮询（30 秒），未接微信订阅消息（属 R3）；催单限频按工单维度统计。
- 知识库问答已升级为 Spring AI + Chroma 向量检索；管理端文章的增删改会自动同步向量库，
  另外 `ingest_kb.py` 仍可批量导入 md（两条来源共存，md 块没有 `kb_id`，靠标题匹配回文章）。
- 文档管理入口尚未进管理端，当前用 `scripts/ingest_kb.py` 手工入库；md 是向量库的唯一语料来源。
- `springdoc.swagger-ui` 已关闭（2.8 在 Spring 6.2 下会生成非法资源通配），文档页用 Knife4j 静态 `doc.html`，接口描述仍由 `/v3/api-docs` 提供。
