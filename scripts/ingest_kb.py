#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知识库入库脚本：Markdown → 滑动窗口切分 → 向量化 → 写入 ChromaDB。

用法示例
--------
# 1) 只看切分结果，不联网、不写库
python ingest_kb.py --path ../docs/kb --dry-run

# 2) 正式入库（读取 ../backend/.env 里的 AI_* / CHROMA_* 配置）
python ingest_kb.py --path ../docs/kb --reset

# 3) 指定单个文件、覆盖集合名
python ingest_kb.py --path ../docs/kb/空调不制冷.md --collection campus_repair_kb

# 4) 没有 embedding 服务时，仅验证「切分 → 写库」链路（向量是占位值，不可检索）
python ingest_kb.py --path ../docs/kb --mock-embedding --reset

依赖：pip install chromadb（--dry-run 与 --mock-embedding 也只需该依赖；
      --dry-run 完全不依赖 chromadb）
"""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

# --------------------------------------------------------------------------
# 与后端 KeywordAgentFallback 保持一致的故障类型关键词
# --------------------------------------------------------------------------
FAULT_TERMS: Dict[str, List[str]] = {
    "空调家电": ["空调", "制冷", "制热", "外机", "遥控", "滤网", "风扇", "冰箱", "洗衣机", "热水器", "家电"],
    "水电": ["跳闸", "断电", "停电", "插座", "开关", "灯", "水管", "漏水", "水龙头", "马桶", "下水", "热水"],
    "木工门窗": ["门锁", "门", "窗", "玻璃", "合页", "抽屉", "柜", "床", "桌", "椅", "锁"],
    "网络多媒体": ["网络", "网线", "wifi", "wi-fi", "投影", "讲台", "电脑", "多媒体", "屏幕", "音响", "麦克风"],
    "电梯": ["电梯", "轿厢", "困人", "楼层"],
    "实验设备": ["实验", "仪器", "设备", "机房"],
}


# --------------------------------------------------------------------------
# 配置
# --------------------------------------------------------------------------
def load_env(path: Path) -> Dict[str, str]:
    """解析 .env（KEY=VALUE），忽略注释与空行，去掉包裹引号。"""
    data: Dict[str, str] = {}
    if not path.exists():
        return data
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[len("export "):]
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        data[key.strip()] = value
    return data


@dataclass
class Config:
    base_url: str
    api_key: str
    embedding_model: str
    dimensions: int
    chroma_host: str
    chroma_port: int
    collection: str
    tenant: str
    database: str
    chunk_size: int
    chunk_overlap: int
    endpoint_source: str = "shared"

    @staticmethod
    def build(args: argparse.Namespace, env: Dict[str, str]) -> "Config":
        def pick(cli: Optional[str], key: str, default: str = "") -> str:
            if cli:
                return cli
            return env.get(key, default)

        def pick_embedding(cli: Optional[str], primary: str, fallback: str) -> Tuple[str, bool]:
            """向量专用配置优先，留空则回落到对话配置。"""
            if cli:
                return cli, True
            value = env.get(primary, "")
            if value:
                return value, True
            return env.get(fallback, ""), False

        raw_base, separate_base = pick_embedding(
            args.embedding_base_url or args.base_url,
            "AI_EMBEDDING_BASE_URL", "AI_BASE_URL")

        # 单独配了向量端点时，不拿对话密钥去试：两家服务商的 key 不通用
        separate_key = bool(args.embedding_api_key or env.get("AI_EMBEDDING_API_KEY"))
        raw_key = args.embedding_api_key or env.get("AI_EMBEDDING_API_KEY", "")
        if not raw_key and not separate_base:
            raw_key = args.api_key or env.get("AI_API_KEY", "")

        base_url = raw_base.rstrip("/")
        # OpenAI 兼容端点通常以 /v1 结尾；缺省时补上，与后端归一化规则保持一致
        if base_url and not base_url.endswith("/v1"):
            base_url += "/v1"

        return Config(
            base_url=base_url,
            api_key=raw_key,
            embedding_model=pick(args.embedding_model, "AI_EMBEDDING_MODEL"),
            dimensions=int(pick(str(args.dimensions) if args.dimensions else None,
                                "AI_EMBEDDING_DIMENSIONS", "1024")),
            chroma_host=pick(args.chroma_host, "CHROMA_HOST", "localhost"),
            chroma_port=int(pick(str(args.chroma_port) if args.chroma_port else None,
                                 "CHROMA_PORT", "8000")),
            collection=pick(args.collection, "CHROMA_COLLECTION", "campus_repair_kb"),
            tenant=pick(None, "CHROMA_TENANT", "default_tenant"),
            database=pick(None, "CHROMA_DATABASE", "default_database"),
            chunk_size=int(args.chunk_size or env.get("RAG_CHUNK_SIZE", "500")),
            chunk_overlap=int(args.chunk_overlap or env.get("RAG_CHUNK_OVERLAP", "80")),
            endpoint_source="separate" if (separate_base or separate_key) else "shared",
        )


# --------------------------------------------------------------------------
# Markdown 解析与切分
# --------------------------------------------------------------------------
FRONT_MATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n", re.S)
HEADING_RE = re.compile(r"^(#{1,6})\s+(.*)$", re.M)


@dataclass
class Section:
    heading: str
    text: str


@dataclass
class Chunk:
    doc_id: str
    doc_title: str
    fault_type: str
    source: str
    section: str
    index: int
    text: str
    char_start: int
    char_end: int

    @property
    def chroma_id(self) -> str:
        return f"{self.doc_id}:{self.index:04d}"

    def metadata(self) -> Dict[str, object]:
        return {
            "doc_id": self.doc_id,
            "source": self.source,
            "title": self.doc_title,
            "fault_type": self.fault_type,
            "section": self.section,
            "chunk_index": self.index,
            "char_start": self.char_start,
            "char_end": self.char_end,
        }


def parse_front_matter(raw: str) -> Tuple[Dict[str, str], str]:
    """极简 front-matter：只支持 key: value，避免额外依赖 PyYAML。"""
    match = FRONT_MATTER_RE.match(raw)
    if not match:
        return {}, raw
    meta: Dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        meta[key.strip().lower()] = value.strip().strip("\"'")
    return meta, raw[match.end():]


def split_sections(body: str) -> List[Section]:
    """按标题切段；没有标题时整篇作为一段。"""
    matches = list(HEADING_RE.finditer(body))
    if not matches:
        text = body.strip()
        return [Section("", text)] if text else []

    sections: List[Section] = []
    for i, match in enumerate(matches):
        start = match.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(body)
        text = body[start:end].strip()
        if text:
            sections.append(Section(match.group(2).strip(), text))
    if not sections:
        text = body.strip()
        if text:
            sections.append(Section("", text))
    return sections


def sliding_window(text: str, size: int, overlap: int) -> Iterable[Tuple[str, int, int]]:
    """字符级滑动窗口；尽量在段落/句号处断开，避免把一句话切成两半。"""
    length = len(text)
    if length <= size:
        stripped = text.strip()
        if stripped:
            yield stripped, 0, length
        return

    start = 0
    while start < length:
        end = min(start + size, length)
        if end < length:
            lower = start + int(size * 0.65)
            candidates = [
                text.rfind("\n\n", lower, end),
                text.rfind("。", lower, end),
                text.rfind("\n", lower, end),
            ]
            cut = max(candidates)
            if cut > lower:
                end = cut + 1
        piece = text[start:end].strip()
        if piece:
            yield piece, start, end
        if end >= length:
            break
        start = max(end - overlap, start + 1)


def infer_fault_type(title: str, section: str, body: str) -> str:
    text = f"{title} {section} {body[:200]}"
    best, best_hits = "", 0
    for fault, terms in FAULT_TERMS.items():
        hits = sum(1 for term in terms if term in text)
        if hits > best_hits:
            best, best_hits = fault, hits
    return best


def build_chunks(path: Path, root: Path, size: int, overlap: int) -> List[Chunk]:
    raw = path.read_text(encoding="utf-8")
    meta, body = parse_front_matter(raw)

    try:
        relative = str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        # 多个 --path 位于不同目录时，退化为文件名
        relative = path.name
    doc_id = hashlib.sha1(relative.encode("utf-8")).hexdigest()[:16]
    sections = split_sections(body)

    title = meta.get("title") or (sections[0].heading if sections and sections[0].heading else path.stem)
    fault_type = meta.get("fault_type") or infer_fault_type(title, "", body)

    chunks: List[Chunk] = []
    for section in sections:
        for text, start, end in sliding_window(section.text, size, overlap):
            chunks.append(Chunk(
                doc_id=doc_id,
                doc_title=title,
                fault_type=fault_type,
                source=relative,
                section=section.heading,
                index=len(chunks),
                text=text,
                char_start=start,
                char_end=end,
            ))
    return chunks


def collect_files(paths: Sequence[Path]) -> Tuple[List[Path], Path]:
    files: List[Path] = []
    root: Optional[Path] = None
    for path in paths:
        if path.is_dir():
            root = root or path
            files.extend(sorted(p for p in path.rglob("*.md") if p.is_file()))
        elif path.is_file():
            root = root or path.parent
            files.append(path)
        else:
            raise SystemExit(f"路径不存在：{path}")
    if not files:
        raise SystemExit("没有找到 .md 文件")
    # 去掉重复
    unique: List[Path] = []
    seen = set()
    for f in files:
        key = f.resolve()
        if key not in seen:
            seen.add(key)
            unique.append(f)
    return unique, root or Path(".")


# --------------------------------------------------------------------------
# 向量化
# --------------------------------------------------------------------------
def embed_batch(cfg: Config, texts: List[str], retries: int = 3) -> List[List[float]]:
    if not cfg.base_url or not cfg.api_key or not cfg.embedding_model:
        raise SystemExit(
            "缺少向量配置：请在 .env 填 AI_EMBEDDING_BASE_URL / AI_EMBEDDING_API_KEY / AI_EMBEDDING_MODEL\n"
            "（后两项留空时会回落到 AI_BASE_URL / AI_API_KEY，但对话模型若不含 embedding 能力就必须单独配置）")
    payload = json.dumps({"model": cfg.embedding_model, "input": texts}).encode("utf-8")
    url = f"{cfg.base_url}/embeddings"

    last_error: Optional[Exception] = None
    for attempt in range(1, retries + 1):
        request = urllib.request.Request(url, data=payload, method="POST")
        request.add_header("Content-Type", "application/json")
        request.add_header("Authorization", f"Bearer {cfg.api_key}")
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                body = json.loads(response.read().decode("utf-8"))
            data = sorted(body.get("data", []), key=lambda item: item.get("index", 0))
            vectors = [item["embedding"] for item in data]
            if len(vectors) != len(texts):
                raise RuntimeError(f"返回向量数不匹配：期望 {len(texts)}，实际 {len(vectors)}")
            return vectors
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                detail = exc.read().decode("utf-8", "replace")[:400]
            except Exception:
                detail = str(exc.reason)
            # 4xx（限流除外）重试也没用，直接给出可读原因
            if 400 <= exc.code < 500 and exc.code != 429:
                raise SystemExit(
                    f"向量化被拒绝（HTTP {exc.code}）：{detail}\n"
                    f"请检查：① AI_EMBEDDING_MODEL={cfg.embedding_model} 是否为该平台可用模型 ID；"
                    f"② AI_EMBEDDING_API_KEY 是否属于 {cfg.base_url} 对应的平台")
            last_error = RuntimeError(f"HTTP {exc.code}：{detail}")
            if attempt < retries:
                wait = 2 ** attempt
                print(f"  ! 向量化失败（第 {attempt} 次）：HTTP {exc.code}，{wait}s 后重试")
                time.sleep(wait)
        except (urllib.error.URLError, KeyError, RuntimeError) as exc:
            last_error = exc
            if attempt < retries:
                wait = 2 ** attempt
                print(f"  ! 向量化失败（第 {attempt} 次）：{exc}，{wait}s 后重试")
                time.sleep(wait)
    raise SystemExit(f"向量化失败：{last_error}")


def mock_embedding(text: str, dimensions: int) -> List[float]:
    """离线占位向量：仅用于验证写库链路，不具备语义检索能力。"""
    digest = hashlib.sha256(text.encode("utf-8")).digest()
    values = [((digest[i % len(digest)] / 255.0) - 0.5) for i in range(dimensions)]
    norm = sum(v * v for v in values) ** 0.5 or 1.0
    return [v / norm for v in values]


# --------------------------------------------------------------------------
# Chroma
# --------------------------------------------------------------------------
def open_collection(cfg: Config, reset: bool, recreate: bool):
    try:
        import chromadb
    except ImportError:
        raise SystemExit("缺少依赖：pip install chromadb")

    target = f"{cfg.chroma_host}:{cfg.chroma_port}"
    try:
        # HttpClient 构造时就会握手，连不上会在这里直接抛 httpx 异常
        client = chromadb.HttpClient(host=cfg.chroma_host, port=cfg.chroma_port)
        client.heartbeat()
    except Exception as exc:
        raise SystemExit(
            f"连不上 Chroma（{target}）：{type(exc).__name__}: {exc}\n"
            "请先启动 Chroma，二选一：\n"
            "  CPS\\chroma\\start.ps1\n"
            "  pip install chromadb && chroma run --path ./chroma-data --port 8000\n"
            f"启动后访问 http://{target}/api/v2/heartbeat 应返回 200；"
            f"端口不同时用 --chroma-port 或改 .env 的 CHROMA_PORT")

    if recreate:
        try:
            client.delete_collection(cfg.collection)
            print(f"已删除旧集合 {cfg.collection}（集合 UUID 会变，后端需重启）")
        except Exception:
            pass
        try:
            return client.create_collection(
                name=cfg.collection,
                metadata={"hnsw:space": "cosine"},
                embedding_function=None,
            )
        except Exception as exc:
            raise SystemExit(f"重建集合 {cfg.collection} 失败：{type(exc).__name__}: {exc}")

    try:
        collection = client.get_or_create_collection(
            name=cfg.collection,
            metadata={"hnsw:space": "cosine"},
            embedding_function=None,
        )
    except Exception as exc:
        raise SystemExit(f"初始化集合 {cfg.collection} 失败：{type(exc).__name__}: {exc}")

    if reset:
        removed = clear_collection(collection)
        print(f"已清空集合 {cfg.collection} 的 {removed} 个块（保留集合，后端无需重启）")
    return collection


def clear_collection(collection) -> int:
    """分页取 id 后删除；保留集合本身，集合 UUID 不变。"""
    removed = 0
    while True:
        page = collection.get(limit=500, include=[])
        ids = page.get("ids") or []
        if not ids:
            break
        collection.delete(ids=ids)
        removed += len(ids)
        if len(ids) < 500:
            break
    return removed


# --------------------------------------------------------------------------
# 主流程
# --------------------------------------------------------------------------
def main() -> int:
    parser = argparse.ArgumentParser(description="Markdown 知识库 → ChromaDB 入库")
    parser.add_argument("--path", action="append", required=True,
                        help="md 文件或目录，可重复指定")
    parser.add_argument("--env", default=None, help=".env 路径，默认 ../backend/.env")
    parser.add_argument("--collection", default=None, help="覆盖 CHROMA_COLLECTION")
    parser.add_argument("--chunk-size", type=int, default=None, help="覆盖 RAG_CHUNK_SIZE")
    parser.add_argument("--chunk-overlap", type=int, default=None, help="覆盖 RAG_CHUNK_OVERLAP")
    parser.add_argument("--batch-size", type=int, default=16, help="向量化批大小，默认 16")
    parser.add_argument("--exclude", action="append", default=[],
                        help="排除的文件名或通配，可重复，如 --exclude README.md")
    parser.add_argument("--reset", action="store_true",
                        help="入库前清空集合内的块（保留集合，后端无需重启）")
    parser.add_argument("--recreate", action="store_true",
                        help="删除并重建集合（换 embedding 模型/维度时用，后端需重启）")
    parser.add_argument("--dry-run", action="store_true", help="只打印切分结果，不联网不写库")
    parser.add_argument("--mock-embedding", action="store_true",
                        help="用占位向量验证写库链路（不可检索，仅联调用）")
    parser.add_argument("--base-url", default=None, help="兼容旧参数，等同 --embedding-base-url")
    parser.add_argument("--api-key", default=None, help="兼容旧参数，等同 --embedding-api-key")
    parser.add_argument("--embedding-base-url", default=None, help="向量端点，覆盖 AI_EMBEDDING_BASE_URL")
    parser.add_argument("--embedding-api-key", default=None, help="向量密钥，覆盖 AI_EMBEDDING_API_KEY")
    parser.add_argument("--embedding-model", default=None)
    parser.add_argument("--dimensions", type=int, default=None)
    parser.add_argument("--chroma-host", default=None)
    parser.add_argument("--chroma-port", type=int, default=None)
    args = parser.parse_args()

    env_path = Path(args.env) if args.env else Path(__file__).resolve().parent.parent / "backend" / ".env"
    env = load_env(env_path)
    if not env:
        print(f"提示：未读取到 {env_path}，将使用命令行参数与默认值")

    cfg = Config.build(args, env)
    if cfg.chunk_overlap >= cfg.chunk_size:
        raise SystemExit("chunk-overlap 必须小于 chunk-size")

    files, root = collect_files([Path(p).resolve() for p in args.path])
    if args.exclude:
        files = [f for f in files
                 if not any(fnmatch.fnmatch(f.name, pat) or fnmatch.fnmatch(str(f), pat)
                            for pat in args.exclude)]
        if not files:
            raise SystemExit("按 --exclude 排除后没有剩余文件")
    source_label = "独立向量端点" if cfg.endpoint_source == "separate" else "沿用对话端点"
    print(f"配置：集合={cfg.collection} 模型={cfg.embedding_model or '(未配置)'} "
          f"端点={cfg.base_url or '(未配置)'}（{source_label}） "
          f"窗口={cfg.chunk_size}/{cfg.chunk_overlap} 文件={len(files)}")

    all_chunks: List[Chunk] = []
    per_file: List[Tuple[str, int]] = []
    for file in files:
        chunks = build_chunks(file, root, cfg.chunk_size, cfg.chunk_overlap)
        all_chunks.extend(chunks)
        try:
            label = str(file.relative_to(root)).replace("\\", "/")
        except ValueError:
            label = file.name
        per_file.append((label, len(chunks)))

    print("\n文件切分结果：")
    for name, count in per_file:
        print(f"  {count:>3} 块  {name}")
    print(f"  合计 {len(all_chunks)} 块，平均 {sum(len(c.text) for c in all_chunks) // max(1, len(all_chunks))} 字/块")

    if args.dry_run:
        sample = all_chunks[0] if all_chunks else None
        if sample:
            print("\n首块预览：")
            print(f"  id={sample.chroma_id} title={sample.doc_title} "
                  f"fault={sample.fault_type} section={sample.section}")
            print("  " + sample.text[:220].replace("\n", "\n  "))
        print("\n--dry-run：未调用向量化、未写库")
        return 0

    collection = open_collection(cfg, args.reset, args.recreate)

    # 按文档先删后写，保证重复执行不产生重复块
    for doc_id in sorted({c.doc_id for c in all_chunks}):
        try:
            collection.delete(where={"doc_id": doc_id})
        except Exception:
            pass

    written = 0
    batch_size = max(1, args.batch_size)
    for start in range(0, len(all_chunks), batch_size):
        batch = all_chunks[start:start + batch_size]
        if args.mock_embedding:
            vectors = [mock_embedding(c.text, cfg.dimensions) for c in batch]
        else:
            vectors = embed_batch(cfg, [c.text for c in batch])
            actual = len(vectors[0])
            if actual != cfg.dimensions:
                print(f"  注意：实际向量维度 {actual}，与 AI_EMBEDDING_DIMENSIONS={cfg.dimensions} 不一致；"
                      f"请把 .env 改成 {actual}（Chroma 集合维度由首次写入决定）")
                cfg.dimensions = actual
        try:
            collection.upsert(
                ids=[c.chroma_id for c in batch],
                embeddings=vectors,
                documents=[c.text for c in batch],
                metadatas=[c.metadata() for c in batch],
            )
        except Exception as exc:
            message = str(exc)
            if "dimension" in message.lower():
                raise SystemExit(
                    f"写入失败：集合 {cfg.collection} 现有维度与当前向量模型不一致。\n"
                    f"换过 embedding 模型（或维度）时请加 --recreate 重建集合，然后重启后端。\n原始错误：{message}")
            raise
        written += len(batch)
        print(f"  已写入 {written}/{len(all_chunks)}")

    total = collection.count()
    print(f"\n完成：本次写入 {written} 块，集合 {cfg.collection} 当前共 {total} 条")
    if args.mock_embedding:
        print("注意：本次使用占位向量，只能验证链路，不能用于语义检索")
    return 0


if __name__ == "__main__":
    sys.exit(main())
