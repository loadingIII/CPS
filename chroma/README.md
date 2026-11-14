# 本地 ChromaDB

向量库的本地实例，数据存放在本目录 `data/`，与 `CPS/backend`、`CPS/scripts` 共用同一个实例。

```
CPS/chroma/
├── start.ps1     # PowerShell 启动（推荐）
├── start.bat     # cmd / 双击启动
└── data/         # 持久化目录（已 gitignore）
```

## 首次准备

```powershell
pip install "chromadb>=1.0"
```

已经装过可跳过。`chroma` 命令随 chromadb 一起安装；若提示找不到命令，
脚本会自动回退到 `python -m chromadb.cli.cli`。

## 启动

```powershell
cd E:\AI-code\大三上\综合设计\campus-repair-system\CPS\chroma
.\start.ps1                 # 默认 8000 端口
.\start.ps1 -Port 8001      # 换端口，同时要改 backend/.env 的 CHROMA_PORT
```

或双击 `start.bat`。首次启动会在 `data/` 下建库，之后启动会复用同一份数据。

**验证**（另开一个终端）：

```powershell
curl http://localhost:8000/api/v2/heartbeat
# {"nanosecond heartbeat":...}  返回这个就说明起来了
```

## 停止

在运行窗口按 `Ctrl+C`。如果窗口已经关了但端口还占着：

```powershell
$p = (Get-NetTCPConnection -LocalPort 8000 -State Listen).OwningProcess
Stop-Process -Id $p -Force
```

## 重置数据

停止服务后删掉 `data/` 目录即可（下次启动会重建空库）：

```powershell
Remove-Item .\data -Recurse -Force
```

> 注意：删库后集合也没了，需要重新执行 `CPS/scripts/ingest_kb.py` 入库；
> 如果后端正在运行，要重启后端，因为 Spring AI 在启动时缓存了集合 UUID。

## 和后端、入库脚本的关系

| 使用方 | 连接配置 |
|---|---|
| 后端 `CPS/backend` | `.env` 的 `CHROMA_HOST` / `CHROMA_PORT` / `CHROMA_COLLECTION` |
| 入库脚本 `CPS/scripts` | 读同一个 `.env`，也可用 `--chroma-host` / `--chroma-port` 覆盖 |

三者必须指向**同一个实例、同一个集合名**，否则会出现「脚本写进去了、后端查不到」。

## 常见问题

**1. 入库脚本报「连不上 Chroma」**
服务没起，或端口不一致。先 `curl http://localhost:8000/api/v2/heartbeat` 确认。

**2. 写入报维度不一致**
换过 embedding 模型（维度变了），旧集合的向量维度对不上。
用 `python ingest_kb.py --path ... --recreate` 重建集合，然后重启后端。

**3. 后端查询 400 / 查不到数据**
- 用 `--reset` 或 `--recreate` 重建过集合 → 集合 UUID 变了，重启后端即可
- `CHROMA_COLLECTION` 与脚本写入的集合名不一致 → 对齐 `.env`

## Java 侧 CRUD

后端已封装 `ChromaAdminService`（`module/agent/chroma`），提供
`stats / list / upsert / deleteByIds / deleteByDocId / clear`，
并通过 `ChromaAdminController` 暴露管理端接口，可直接用 curl 验证：

```powershell
# 集合概况（UUID、条数）
curl http://localhost:8080/api/agent/chroma/stats -H "Authorization: Bearer <admin-token>"

# 前 20 个块
curl "http://localhost:8080/api/agent/chroma/chunks?limit=20&offset=0" -H "Authorization: Bearer <admin-token>"

# 写入（会自动调用 embedding 模型）
curl -X POST http://localhost:8080/api/agent/chroma/chunks ^
  -H "Content-Type: application/json" -H "Authorization: Bearer <admin-token>" ^
  -d "[{\"id\":\"demo:0001\",\"text\":\"空调不制冷先检查遥控器模式\",\"metadata\":{\"doc_id\":\"demo\",\"title\":\"演示文档\",\"fault_type\":\"空调家电\"}}]"

# 删除单个块 / 按文档删除 / 清空全部
curl -X DELETE http://localhost:8080/api/agent/chroma/chunks/demo:0001 -H "Authorization: Bearer <admin-token>"
curl -X DELETE http://localhost:8080/api/agent/chroma/docs/demo -H "Authorization: Bearer <admin-token>"
curl -X POST http://localhost:8080/api/agent/chroma/clear -H "Authorization: Bearer <admin-token>"
```

`clear` 只删块、保留集合本身（UUID 不变），所以清空后**不需要**重启后端。
