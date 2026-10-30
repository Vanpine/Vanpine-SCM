"""
RAG 知识库服务模块
负责文档切分（chunk_document）、语义检索（retrieve）、文档解析索引（parse_and_index）
复用 embedding.py 的在线 Embedding 与 milvus_store.py 的 ai_doc_chunks 集合
"""
import re  # 正则：按段落/句子切分
import io  # 内存字节流
import json  # JSON 序列化
import os  # 环境变量（父块预算可配）
import logging  # 日志模块
import uuid  # 生成确定性分块 ID（Milvus 主键需 int64，raw id 经 store 映射）
import httpx  # HTTP 客户端（下载 OSS 文件，连接池复用）
from app.services.embedding import encode, encode_batch  # Embedding 编码（单条/批量）
from app.services.chunker import extract_blocks, build_chunks, build_parent_child  # 结构化/父子切分
from app.services.tokenizer import count_tokens  # token 计量（父块预算截断用）
from app.store.milvus_store import upsert_chunks, query_chunks  # AI 文档向量写入/检索

logger = logging.getLogger("srm-ai.rag")  # 模块日志器

# ==================== 切分配置 ====================

# 检索召回块数（拼入 LLM 上下文）
RETRIEVE_TOP_K = 5
# 初召回候选数（rerank 前的池子；无 rerank 时即最终条数）
RETRIEVE_PREFETCH = 20
# 单条父块注入 LLM 的 token 上限：父块聚合目标 1200，留裕量防个别父块超长挤爆上下文
PARENT_MAX_TOKENS = int(os.environ.get("PARENT_MAX_TOKENS", "1600"))

# 共享 HTTP 客户端：下载 OSS 文档时复用连接（大文件下载，超时单独传 60s）
_client = httpx.Client(limits=httpx.Limits(max_connections=8, max_keepalive_connections=4))


def chunk_document(text: str) -> list[str]:
    """将文档全文切分为若干文本块（兼容包装，返回纯文本列表）。

    实际走结构化切分（见 chunker.py）：结构边界为主、递归字符兜底。
    保留此函数签名，供历史调用方与测试使用。

    Args:
        text: 文档全文（已从 txt/md/pdf 抽取的纯文本）

    Returns:
        文本块列表
    """
    if not text or not text.strip():
        return []
    chunks = build_chunks(extract_blocks(text))
    texts = [c["content"] for c in chunks]
    logger.info("文档切分完成: %d 块", len(texts))
    return texts


def retrieve(query: str, tenant_id: int, top_k: int = RETRIEVE_TOP_K,
             tool_token: str = "") -> list[dict]:
    """语义检索：提问向量化 → 召回子块 → 按 parent 回填父块上下文

    parent-child：子块（小、精）用于命中，命中后取同父块全文喂 LLM，
    保证「检索准 + 上下文全」。回填失败（父块缺失/令牌缺失）时退回子块原文。

    Args:
        query: 用户提问
        tenant_id: 租户ID（向量检索按此过滤，防跨租户知识泄露）
        top_k: 召回块数
        tool_token: AI 工具令牌（回调 Java 取父块用；空则不做父块回填）

    Returns:
        [{"document_id", "file_name", "chunk_index", "content", "score", ...}]
        同父块的多子块已合并为一条（content 为父块全文）
    """
    if not query or not query.strip():
        return []
    query_embedding = encode(query)  # 提问向量化
    # 初召回扩大候选池 → 子块 rerank 粗筛 → 父块回填 → 父块二次 rerank 定序
    # 关键：先回填父块、再对「注入的内容」打分——否则 rerank 用子块打分、
    # 注入却是父块全文，排序与实际喂给 LLM 的内容不一致，排序不可信。
    candidates = query_chunks(query_embedding, tenant_id, max(top_k, RETRIEVE_PREFETCH))
    ranked = _rerank(query, candidates, max(top_k, RETRIEVE_PREFETCH))
    filled = _backfill_parents(ranked, tool_token)
    results = _rerank_parents(query, filled, top_k)
    # 结构化观测日志：query / 候选数 / rerank 分数 / 最终注入数 / 是否低置信
    scores = [round(c.get("rerank_score"), 4) for c in ranked if c.get("rerank_score") is not None]
    low_confidence = bool(candidates) and not ranked
    logger.info(
        "RAG 检索: tenant=%s query=%r 候选=%d rerank分数=%s 注入=%d 低置信拒答=%s",
        tenant_id, (query or "")[:60], len(candidates), scores, len(results), low_confidence,
    )
    return results


def _rerank_parents(query: str, filled: list[dict], top_k: int) -> list[dict]:
    """对回填后的父块做二次 rerank（打分对象 = 注入对象），并按上下文预算截断。

    - 未启用 rerank / 无 key 时：退回原序取 top_k（保持旧行为，不额外调用）。
    - 二次 rerank 失败：退回原序取 top_k（父块回填本身有价值，不该因重排失败丢结果）。
    - 父块被 PARENT_MAX_TOKENS 截断，防单条超大父块挤爆上下文。
    """
    if not filled:
        return []
    # 先按 token 预算截断父块，再打分——保证打分内容与最终注入内容一致
    for f in filled:
        content = f.get("content") or ""
        if count_tokens(content) > PARENT_MAX_TOKENS:
            f["content"] = _truncate_tokens(content, PARENT_MAX_TOKENS)
    try:
        from app.services.rerank import rerank, RERANK_ENABLED, _API_KEY
        # rerank 未启用/无 key 时不调用（rerank 内部会退回原序，但这里显式短路省一次判断）
        if not RERANK_ENABLED or not _API_KEY:
            return filled[:top_k]
        # 复用交叉编码器：把父块全文当候选重新打分；top_k 即最终注入条数
        reranked = rerank(query, filled, top_k)
        return reranked or filled[:top_k]
    except Exception as e:
        logger.warning("父块二次 rerank 失败，退回原序: %s", e)
        return filled[:top_k]


def _truncate_tokens(text: str, max_tokens: int) -> str:
    """按 token 预算截断文本（在句边界收口，避免半句话）。"""
    if count_tokens(text) <= max_tokens:
        return text
    # 二分找不超预算的最大字符切点
    lo, hi = 1, len(text)
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if count_tokens(text[:mid]) <= max_tokens:
            lo = mid
        else:
            hi = mid - 1
    cut = text[:lo]
    # 回退到最近的句末标点，避免截在句子中间
    for sep in ("。", "！", "？", "；", "\n"):
        pos = cut.rfind(sep)
        if pos > len(cut) * 0.6:  # 句末在末段才采用，否则宁可硬截
            return cut[:pos + 1]
    return cut


def _rerank(query: str, candidates: list[dict], top_k: int) -> list[dict]:
    """交叉编码器重排（含置信度阈值过滤）。

    rerank 失败时返回空（不回退原序——弱相关候选注入会污染回答），
    由上层据此走低置信拒答。
    """
    if not candidates:
        return []
    try:
        from app.services.rerank import rerank
        return rerank(query, candidates, top_k)
    except Exception as e:
        logger.warning("rerank 调用异常，返回空（交上层低置信拒答）: %s", e)
        return []


def _backfill_parents(children: list[dict], tool_token: str = "") -> list[dict]:
    """按 parent_id 去重，取父块全文回填；无 parent 或父块缺失时保留子块原文。

    父块存 Java 侧 MySQL，经 java_client 按 parent_key 回查（带 tool_token 鉴权）。
    同父块的多个子块只保留一条（避免同一章节重复占满上下文）。
    """
    if not children:
        return []
    parent_ids = [c.get("parent_id") for c in children if c.get("parent_id")]
    parents: dict[str, dict] = {}
    if parent_ids and tool_token:
        try:
            from app.services.java_client import fetch_parents
            # parent_id 在写入时已加 document_id 前缀，跨文档唯一
            parents = fetch_parents(parent_ids, tool_token)
        except Exception as e:
            logger.warning("父块回填失败，退回子块原文: %s", e)

    seen_parent: set[str] = set()
    out: list[dict] = []
    for c in children:
        pid = c.get("parent_id")
        if pid:
            if pid in seen_parent:
                continue  # 同父块已收，跳过
            seen_parent.add(pid)
        item = dict(c)
        if pid and pid in parents:
            p = parents[pid]
            item["content"] = p.get("content") or c.get("content", "")
            item["heading_path"] = p.get("heading_path") or c.get("heading_path", [])
            item["chunk_type"] = "parent"
        out.append(item)
    return out


def parse_and_index(document_id: int, tenant_id: int, oss_url: str,
                    file_name: str, file_type: str) -> tuple[int, list[dict]]:
    """下载文档 → 抽取结构块 → 父子切块 → 子块向量化写 Milvus，父块回传给 Java 落库

    Args:
        document_id: 文档ID（对应 ai_documents.id，作为 Milvus payload）
        tenant_id: 租户ID
        oss_url: OSS 预签名下载地址
        file_name: 原始文件名
        file_type: 文件类型 txt / md / pdf / docx / xlsx / xls

    Returns:
        (子块数量, 父块列表[{parent_key, content, heading_path, token_count}])
        父块由 Java 侧落 MySQL，检索时再回调取回。
    """
    # 1. 下载原始文件字节
    raw_bytes = _download(oss_url)
    # 2. 抽取结构块（txt/md 走正则；docx 走样式层级；pdf 走纯文本兜底）
    blocks = _extract_blocks(raw_bytes, file_type)
    if not blocks:
        raise ValueError("文档内容为空，无法解析")
    # 3. parent-child 两层切分（子块进向量，父块回填）
    children, parents = build_parent_child(blocks)
    if not children:
        raise ValueError("文档切分后无有效内容")
    # 4. 批量向量化 + 组装 payload（批量摊薄在线 HTTP 往返）
    #    embed_text = 文件名 > 章节路径 + 正文（章节路径进向量提升定位语义）
    #    content 存正文原样，供引用展示；parent_id 供检索侧回填父块全文
    embed_texts = []
    for chunk in children:
        heading_path = chunk.get("heading_path") or []
        section = " > ".join(p for p in [file_name or ""] + heading_path if p)
        content = chunk["content"]
        embed_texts.append(f"{section}\n{content}" if section else content)
    vectors = encode_batch(embed_texts)
    points = []
    for idx, chunk in enumerate(children):
        content = chunk["content"]
        heading_path = chunk.get("heading_path") or []
        points.append({
            # Qdrant point id 仅接受 uint64 或 UUID，不能用 "文档ID-序号" 字符串；
            # 用 UUID5 从 (document_id, chunk_index) 生成确定性 UUID，重跑幂等
            "id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"{document_id}-{idx}")),
            "vector": vectors[idx],
            "payload": {
                "tenant_id": str(tenant_id),
                "document_id": str(document_id),
                "chunk_index": idx,
                "file_name": file_name or "",
                "content": content,
                # 结构元数据：供检索侧补全来源、引用展示章节
                "heading_path": heading_path,
                "chunk_type": chunk.get("chunk_type", "para"),
                "token_count": chunk.get("token_count", 0),
                # parent-child：父块 id（加文档前缀保证跨文档唯一）
                "parent_id": _parent_pid(document_id, chunk.get("parent_id")),
            },
        })
    # 父块：不进 Milvus，随解析响应回传 Java 落 MySQL（ai_doc_parent_chunks）
    parent_items = [{
        "parent_key": _parent_pid(document_id, p["parent_id"]),
        "content": p["content"],
        "heading_path": p.get("heading_path") or [],
        "token_count": p.get("token_count", 0),
    } for p in parents]
    # 5. 批量写 Milvus（先按文档清旧子块向量，防止块数变少残留脏点）
    try:
        from app.store.milvus_store import delete_by_document
        delete_by_document(str(document_id), tenant_id)
    except Exception as e:  # 首次解析无旧向量，删除失败不阻断
        logger.debug("清旧向量跳过: document_id=%s, %s", document_id, e)
    upsert_chunks(points)
    logger.info("文档解析索引完成: document_id=%s, 子块=%d, 父块=%d",
                document_id, len(points), len(parent_items))
    return len(points), parent_items


def _parent_pid(document_id: int, parent_id: str | None) -> str:
    """父块确定性 key：document_id + 局部 parent_id → UUID5（跨文档唯一、重跑幂等）。

    与子块 payload.parent_id 为同一值，Java 侧存为 ai_doc_parent_chunks.parent_key。
    """
    if not parent_id:
        return ""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"{document_id}-parent-{parent_id}"))


def _download(url: str) -> bytes:
    """下载文件字节（httpx，超时 60s）"""
    try:
        response = _client.get(url, timeout=60)
        response.raise_for_status()
        return response.content
    except Exception as e:
        logger.error("文档下载失败: %s", e, exc_info=True)
        raise RuntimeError(f"文档下载失败: {e}") from e


def _extract_blocks(raw_bytes: bytes, file_type: str) -> list:
    """按文件类型抽取结构块列表。

    - txt/md：解码为纯文本，走正则结构识别（chunker.extract_blocks）
    - docx：走 python-docx 样式层级（docx_parser.extract_docx_blocks）
    - pdf：pypdf 逐页抽文本，再走正则结构识别（页码暂不保留，P4 升级）

    Args:
        raw_bytes: 文件字节
        file_type: txt / md / docx / pdf

    Returns:
        Block 列表

    Raises:
        ValueError: 类型不支持或内容为空
        RuntimeError: 依赖未安装
    """
    from app.services.chunker import Block  # 局部导入避免循环依赖

    ft = (file_type or "").lower()
    if ft in ("txt", "md"):
        text = _decode_text(raw_bytes)
        return extract_blocks(text)
    if ft == "docx":
        try:
            from app.services.docx_parser import extract_docx_blocks
        except ImportError:
            raise RuntimeError("python-docx 未安装，无法解析 docx") from None
        return extract_docx_blocks(raw_bytes)
    if ft in ("xlsx", "xls"):
        try:
            from app.services.excel_parser import extract_excel_blocks
        except ImportError:
            raise RuntimeError("openpyxl 未安装，无法解析 Excel") from None
        return extract_excel_blocks(raw_bytes)
    if ft == "pdf":
        try:
            from app.services.pdf_parser import extract_pdf_blocks
        except ImportError:
            raise RuntimeError("pypdf 未安装，无法解析 PDF") from None
        return extract_pdf_blocks(raw_bytes)
    raise ValueError(f"不支持的文件类型: {file_type}")


def _decode_text(raw_bytes: bytes) -> str:
    """按 UTF-8 → GBK → Latin-1 依次尝试解码文本文件。"""
    for encoding in ("utf-8", "gbk", "latin-1"):
        try:
            return raw_bytes.decode(encoding)
        except (UnicodeDecodeError, LookupError):
            continue
    raise ValueError("文档编码无法识别")
