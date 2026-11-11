"""
Milvus 向量库管理模块
替代 Qdrant（自建远程 Qdrant 6333 迁移到 Milvus 19530）。
函数签名与 qdrant_store.py 完全兼容，matching.py / rag.py / routes.py 无需改动调用方式。

设计要点：
- 用 MilvusClient 的 host/port 双参连接（v2 standalone），惰性建 client 单例。
- 三个 collection：supplier_profiles / open_requirements / ai_doc_chunks，各自独立。
- 主键是 int64（Milvus 主键只支持 int64 或 varchar）。为兼容历史调用，
  对外仍用字符串 id（tenant_id / requirement_id / 分块 UUID），写入时映射为内部 int64。
- 租户隔离：tenant_id 存为 int64 标量字段，检索时用 filter="tenant_id == N"。
  supplier_profiles 是全局召回、结果侧按 tenant_id 处理；ai_doc_chunks 必须过滤租户。
- 元数据 payload 统一放动态字段 metadata_json（JSON 字符串），读时反序列化还原，
  避免 Milvus 动态字段对嵌套 list[dict]（heading_path）的类型限制。
"""
import hashlib  # 字符串 id → 稳定 int64（UUID/雪花降级用）
import json  # payload 序列化（metadata_json）
import logging  # 日志模块
import os  # 环境变量
from typing import Any  # 类型标注

from pymilvus import (  # Milvus 2.x 客户端 + 字段类型
    AnnSearchRequest,
    DataType,
    Function,
    FunctionType,
    MilvusClient,
    RRFRanker,
)

logger = logging.getLogger("srm-ai.milvus")

# ==================== 配置 ====================

COLLECTION_NAME = "supplier_profiles"
# 公开采购需求集合（找需求语义召回，与画像隔离）
REQ_COLLECTION_NAME = "open_requirements"
# AI 知识库文档分块集合（与 supplier_profiles 隔离，互不污染）
# v2：Milvus 2.5 原生 BM25（text → sparse 由 Function 自动生成）+ dense 混合检索。
# 旧集合 ai_doc_chunks 是 dense-only，无法原地加 Function，需重灌到 v2。
DOC_COLLECTION_NAME = os.environ.get("DOC_COLLECTION_NAME", "ai_doc_chunks_v2")

# Milvus 连接地址 — 默认指向内网 Milvus，可通过环境变量覆盖
MILVUS_HOST = os.environ.get("MILVUS_HOST", "118.31.5.158")
MILVUS_PORT = int(os.environ.get("MILVUS_PORT", "19530"))
# 向量维度 — 与 embedding.py 的 VECTOR_DIM 保持一致（默认 1024）
VECTOR_DIM = int(os.environ.get("EMBEDDING_VECTOR_DIM", "1024"))
# 相似度度量：与 Qdrant 的 COSINE 对齐
METRIC_TYPE = "COSINE"
# 向量字段名
_VECTOR_FIELD = "vector"

# Milvus 主键只支持 int64 或 varchar；统一用 int64 主键 + 内部 id 映射。
# 集合字段（标量字段随 collection 固定，动态字段 metadata_json 存业务 payload）。
_PK_FIELD = "uid"
_TENANT_FIELD = "tenant_id"
_META_FIELD = "metadata_json"
# 仅知识库分块集合使用：document_id 映射为独立标量字段，供按文档批量删除过滤
_DOC_ID_FIELD = "doc_id"
# BM25 输入文本字段（原文由 Milvus analyzer 分词）与自动生成的稀疏向量字段
_TEXT_FIELD = "text"
_SPARSE_FIELD = "sparse_vector"
# BM25 文本字段最大字符数（子块 ≤512 token，16384 足够并留足余量）
_TEXT_MAX_CHARS = 16384
# 混合检索单路召回条数：给 RRF 融合留冗余，融合后再交给 rerank 截断
HYBRID_BRANCH_LIMIT = 50
# RRF 常数（业界默认 60）
RRF_K = 60
# BM25 精确命中保送条数：RRF 奖励「两路都出现」，会把只在关键词路排第一的编号/
# 专有名词压到融合结果之外；单独取 BM25 前 N 名强制并入候选窗口（0=关闭）
HYBRID_KEYWORD_GUARANTEE = int(os.environ.get("HYBRID_KEYWORD_GUARANTEE", "3"))

_client: MilvusClient | None = None


# ==================== 客户端与集合管理 ====================

def _get_client() -> MilvusClient:
    """获取 Milvus 客户端单例（host/port 双参连接）。"""
    global _client
    if _client is None:
        logger.info("连接 Milvus: %s:%d", MILVUS_HOST, MILVUS_PORT)
        _client = MilvusClient(uri=f"http://{MILVUS_HOST}:{MILVUS_PORT}")
    return _client


def _id_to_int(value: Any) -> int:
    """字符串/数字 id → 稳定 int64（Milvus 主键）。

    纯数字字符串直接转 int；UUID 等非数字用 blake2b 摘要取 63 位正整数，
    冲突概率可忽略且重跑幂等（同一 id 始终映射同一 int）。
    """
    s = str(value)
    if s.lstrip("-").isdigit():
        return int(s)
    digest = hashlib.blake2b(s.encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, "big") & 0x7FFFFFFFFFFFFFFF  # 取正 int64


def _create_collection(name: str) -> None:
    """按统一 schema 创建集合：int64 主键 + 向量 + tenant_id 标量 + 动态元数据。

    enable_dynamic_field=True 让未声明的键也可写入，但本模块只显式写受控字段。
    """
    client = _get_client()
    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
    schema.add_field(_PK_FIELD, DataType.INT64, is_primary=True)
    schema.add_field(_VECTOR_FIELD, DataType.FLOAT_VECTOR, dim=VECTOR_DIM)
    schema.add_field(_TENANT_FIELD, DataType.INT64)
    schema.add_field(_META_FIELD, DataType.VARCHAR, max_length=65535)

    index_params = client.prepare_index_params()
    index_params.add_index(field_name=_VECTOR_FIELD, index_type="AUTOINDEX", metric_type=METRIC_TYPE)
    # 标量过滤字段建 INVERTED 索引：tenant_id 用于检索/删除过滤。
    # Milvus 对标量字段无索引时会退化为段内扫描，数据量大时拖慢检索。
    index_params.add_index(field_name=_TENANT_FIELD, index_type="INVERTED")

    client.create_collection(collection_name=name, schema=schema, index_params=index_params)
    logger.info("Milvus 集合已创建: %s, dim=%d, metric=%s", name, VECTOR_DIM, METRIC_TYPE)


def _create_doc_collection(name: str) -> None:
    """创建知识库分块集合：dense + 原生 BM25（text 由 analyzer 分词后自动生成 sparse）。

    Milvus 2.5+ 的 BM25 Function 只能在建集合时声明，已有集合无法原地追加，
    因此旧 dense-only 集合必须重灌到本集合（默认 ai_doc_chunks_v2）。
    """
    client = _get_client()
    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
    schema.add_field(_PK_FIELD, DataType.INT64, is_primary=True)
    schema.add_field(_VECTOR_FIELD, DataType.FLOAT_VECTOR, dim=VECTOR_DIM)
    # 中文分词：Milvus 内置 chinese analyzer（jieba + cnalphanumonly）
    schema.add_field(_TEXT_FIELD, DataType.VARCHAR, max_length=_TEXT_MAX_CHARS,
                     enable_analyzer=True, analyzer_params={"type": "chinese"})
    schema.add_field(_SPARSE_FIELD, DataType.SPARSE_FLOAT_VECTOR)
    schema.add_field(_TENANT_FIELD, DataType.INT64)
    schema.add_field(_META_FIELD, DataType.VARCHAR, max_length=65535)
    schema.add_field(_DOC_ID_FIELD, DataType.INT64)
    schema.add_function(Function(
        name="doc_bm25",
        function_type=FunctionType.BM25,
        input_field_names=[_TEXT_FIELD],
        output_field_names=[_SPARSE_FIELD],
    ))

    index_params = client.prepare_index_params()
    index_params.add_index(field_name=_VECTOR_FIELD, index_type="AUTOINDEX", metric_type=METRIC_TYPE)
    index_params.add_index(field_name=_SPARSE_FIELD,
                           index_type="SPARSE_INVERTED_INDEX", metric_type="BM25")
    index_params.add_index(field_name=_TENANT_FIELD, index_type="INVERTED")
    index_params.add_index(field_name=_DOC_ID_FIELD, index_type="INVERTED")

    client.create_collection(collection_name=name, schema=schema, index_params=index_params)
    logger.info("Milvus 文档集合已创建: %s, dim=%d, dense+bgem25", name, VECTOR_DIM)


def _expected_fields() -> dict:
    """供应商/需求集合的字段契约（字段名 → Milvus 类型）。"""
    return {
        _PK_FIELD: DataType.INT64,
        _VECTOR_FIELD: DataType.FLOAT_VECTOR,
        _TENANT_FIELD: DataType.INT64,
        _META_FIELD: DataType.VARCHAR,
    }


def _expected_doc_fields() -> dict:
    """知识库文档集合的字段契约：在基础字段上增加 text / sparse / doc_id。"""
    expected = _expected_fields()
    expected.update({
        _TEXT_FIELD: DataType.VARCHAR,
        _SPARSE_FIELD: DataType.SPARSE_FLOAT_VECTOR,
        _DOC_ID_FIELD: DataType.INT64,
    })
    return expected


def _check_fields(name: str, fields: list, expected: dict) -> None:
    """比对字段名/类型，不一致即报错（见 _assert_schema 说明）。"""
    actual = {f["name"]: f["type"] for f in fields}
    mismatched = [n for n, t in expected.items() if actual.get(n) != t]
    if mismatched:
        raise RuntimeError(
            f"Milvus 集合 {name} 的 schema 与当前代码不匹配，"
            f"以下字段缺失或类型不符: {', '.join(mismatched)}；"
            f"实际字段: {', '.join(sorted(actual))}。"
            f"该集合由旧版本创建，需先删除后由本服务按新 schema 重建。"
        )


def _assert_schema(name: str, expected: dict) -> None:
    """校验已存在集合的字段与期望一致。

    集合存在但字段名/类型对不上（典型：由旧版本代码创建）时直接报错——
    否则写入会抛晦涩的 DataNotMatchException，且可能被上层静默吞掉。

    Raises:
        RuntimeError: 字段缺失或类型不符，需先删除旧集合再由本服务重建。
    """
    client = _get_client()
    _check_fields(name, client.describe_collection(name)["fields"], expected)


def _assert_doc_schema(name: str) -> None:
    """在字段校验之外，校验 BM25 Function 是否存在。

    缺 Function 时 sparse 字段永远不会被填充，检索会静默退化为 dense-only，
    因此视为 schema 不匹配直接报错。只取一次 describe_collection，避免每轮检索
    多一次元数据 RPC。
    """
    client = _get_client()
    description = client.describe_collection(name)
    _check_fields(name, description.get("fields") or [], _expected_doc_fields())
    functions = description.get("functions") or []
    has_bm25 = any(
        int(fn.get("type", -1)) == int(FunctionType.BM25)
        and _TEXT_FIELD in str(fn.get("input_field_names", ""))
        and _SPARSE_FIELD in str(fn.get("output_field_names", ""))
        for fn in functions
    )
    if not has_bm25:
        raise RuntimeError(
            f"Milvus 集合 {name} 缺少 BM25 Function（{_TEXT_FIELD} → {_SPARSE_FIELD}），"
            "无法进行混合检索；需删除后由本服务按新 schema 重建。"
        )


def _ensure_collection(name: str) -> None:
    """确保供应商/需求集合存在，且 schema 与当前代码一致。"""
    try:
        client = _get_client()
        if client.has_collection(name):
            _assert_schema(name, _expected_fields())
        else:
            _create_collection(name)
    except RuntimeError:
        raise  # schema 不匹配已是明确错误，保留原始信息与堆栈
    except Exception as e:
        logger.error("Milvus 集合初始化失败: %s, %s", name, e, exc_info=True)
        raise RuntimeError(f"Milvus 集合初始化失败: {name}") from e


def _ensure_doc_collection() -> None:
    """确保知识库文档集合存在，且 schema + BM25 Function 与代码一致。"""
    try:
        client = _get_client()
        if client.has_collection(DOC_COLLECTION_NAME):
            _assert_doc_schema(DOC_COLLECTION_NAME)
        else:
            _create_doc_collection(DOC_COLLECTION_NAME)
    except RuntimeError:
        raise
    except Exception as e:
        logger.error("Milvus 文档集合初始化失败: %s, %s", DOC_COLLECTION_NAME, e, exc_info=True)
        raise RuntimeError(f"Milvus 文档集合初始化失败: {DOC_COLLECTION_NAME}") from e


def _search(collection: str, query_embedding: list[float], top_k: int,
            filter_expr: str = "") -> list[dict]:
    """统一检索：返回 [{id, distance, meta}]，meta 为反序列化后的 payload。

    Milvus COSINE 返回的是相似度（越大越相似），与 Qdrant 的 score 语义一致；
    本函数转成 cosine 距离（1 - score）供上层沿用旧逻辑。
    """
    client = _get_client()
    results = client.search(
        collection_name=collection,
        data=[query_embedding],
        # 必须显式指定 dense 字段：知识库集合同时存在 dense/sparse 两个向量字段，
        # 不指定时 Milvus 报 "multiple anns_fields exist"，混合检索降级会直接失败。
        anns_field=_VECTOR_FIELD,
        limit=top_k,
        filter=filter_expr or None,
        output_fields=[_PK_FIELD, _META_FIELD, _TENANT_FIELD],
        search_params={"metric_type": METRIC_TYPE},
        # Strong 一致性：写入后立即可检索。默认 Bounded 有可见性延迟，
        # 会导致「刚 upsert 的画像/文档立刻检索查不到」，问答场景不可接受。
        consistency_level="Strong",
    )
    out = []
    # Milvus search 返回 list[list[hit]]（每个 query 一组）
    for hit in (results[0] if results else []):
        entity = hit.get("entity") or {}
        meta = _load_meta(entity.get(_META_FIELD))
        out.append({
            "id": hit.get("id"),
            "score": hit.get("distance", 0.0),
            "distance": 1.0 - (hit.get("distance") or 0.0),
            "tenant_id": entity.get(_TENANT_FIELD),
            "metadata": meta,
        })
    return out


def _dump_meta(metadata: dict) -> str:
    """把 payload dict 序列化为 JSON 字符串（含 heading_path 等嵌套结构）。"""
    return json.dumps(metadata or {}, ensure_ascii=False)


def _load_meta(raw) -> dict:
    """把 metadata_json 还原为 dict；解析失败返回空 dict（不阻断检索）。"""
    if not raw:
        return {}
    if isinstance(raw, dict):
        return raw
    try:
        return json.loads(raw)
    except Exception:
        logger.warning("metadata_json 解析失败，返回空 payload")
        return {}


# ==================== 供应商画像集合操作 ====================

def upsert_embedding(tenant_id: int, embedding: list[float], metadata: dict[str, str]) -> None:
    """插入或更新供应商 Embedding（按 tenant_id 去重）。

    Raises:
        ValueError: 向量为空或维度与当前 Embedding 配置不一致。
        RuntimeError: Milvus 写入失败。
    """
    if not embedding:
        raise ValueError(f"tenantId={tenant_id} 的向量为空")
    if len(embedding) != VECTOR_DIM:
        raise ValueError(
            f"tenantId={tenant_id} 的向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(embedding)}"
        )

    _ensure_collection(COLLECTION_NAME)
    client = _get_client()
    try:
        client.upsert(
            collection_name=COLLECTION_NAME,
            data=[{
                _PK_FIELD: _id_to_int(tenant_id),
                _VECTOR_FIELD: embedding,
                _TENANT_FIELD: int(tenant_id),
                _META_FIELD: _dump_meta(metadata),
            }],
        )
        logger.debug("向量 upsert 成功: tenantId=%s", tenant_id)
    except Exception as e:
        logger.error("向量 upsert 失败: tenantId=%s, error=%s", tenant_id, e, exc_info=True)
        raise RuntimeError(f"tenantId={tenant_id} 的向量写入失败") from e


def delete_embedding(tenant_id: int) -> None:
    """删除指定供应商的 Embedding。

    Raises:
        RuntimeError: Milvus 删除失败。
    """
    try:
        _ensure_collection(COLLECTION_NAME)
        client = _get_client()
        client.delete(collection_name=COLLECTION_NAME, ids=[_id_to_int(tenant_id)])
        logger.debug("向量删除成功: tenantId=%s", tenant_id)
    except Exception as e:
        logger.error("向量删除失败: tenantId=%s, error=%s", tenant_id, e, exc_info=True)
        raise RuntimeError(f"tenantId={tenant_id} 的向量删除失败") from e


def query_similar(query_embedding: list[float], top_k: int = 100) -> list[dict]:
    """按余弦相似度检索最相似的供应商。

    Raises:
        ValueError: 查询向量为空或维度不匹配。
        RuntimeError: Milvus 检索失败。
    """
    if not query_embedding:
        raise ValueError("语义检索向量不能为空")
    if len(query_embedding) != VECTOR_DIM:
        raise ValueError(
            f"语义检索向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(query_embedding)}"
        )

    try:
        _ensure_collection(COLLECTION_NAME)
        rows = _search(COLLECTION_NAME, query_embedding, top_k)
        out = []
        for r in rows:
            # 复用 Qdrant 版返回结构：tenant_id 从标量字段取，metadata 保持原 payload
            tid = r["tenant_id"]
            out.append({
                "tenant_id": int(tid) if tid is not None else None,
                "metadata": r["metadata"],
                "distance": r["distance"],
            })
        logger.debug("向量检索完成: top_k=%d, 返回 %d 条", top_k, len(out))
        return out
    except Exception as e:
        logger.error("向量检索失败: %s", e, exc_info=True)
        raise RuntimeError("Milvus 语义检索失败") from e


# ==================== 公开需求集合操作 ====================

def upsert_requirement(requirement_id: int, embedding: list[float], metadata: dict) -> None:
    """插入或更新公开需求向量（按 requirement_id 去重）。"""
    if not embedding or len(embedding) != VECTOR_DIM:
        raise ValueError(f"requirementId={requirement_id} 的向量非法")
    _ensure_collection(REQ_COLLECTION_NAME)
    client = _get_client()
    try:
        client.upsert(
            collection_name=REQ_COLLECTION_NAME,
            data=[{
                _PK_FIELD: _id_to_int(requirement_id),
                _VECTOR_FIELD: embedding,
                _TENANT_FIELD: 0,
                _META_FIELD: _dump_meta(metadata),
            }],
        )
    except Exception as e:
        logger.error("需求向量写入失败: requirementId=%s, error=%s", requirement_id, e, exc_info=True)
        raise RuntimeError(f"requirementId={requirement_id} 的向量写入失败") from e


def delete_requirement(requirement_id: int) -> None:
    """删除公开需求向量。"""
    try:
        _ensure_collection(REQ_COLLECTION_NAME)
        client = _get_client()
        client.delete(collection_name=REQ_COLLECTION_NAME, ids=[_id_to_int(requirement_id)])
    except Exception as e:
        logger.error("需求向量删除失败: requirementId=%s, error=%s", requirement_id, e, exc_info=True)
        raise RuntimeError(f"requirementId={requirement_id} 的向量删除失败") from e


def query_similar_requirements(query_embedding: list[float], top_k: int = 100) -> list[dict]:
    """按画像向量检索最相似的公开需求。"""
    if not query_embedding or len(query_embedding) != VECTOR_DIM:
        raise ValueError("需求语义检索向量非法")
    try:
        _ensure_collection(REQ_COLLECTION_NAME)
        rows = _search(REQ_COLLECTION_NAME, query_embedding, top_k)
        out = []
        for r in rows:
            meta = r["metadata"]
            # requirement_id 由 payload 里的原始 id 还原（int64 主键为哈希映射，非原始值）
            rid = meta.get("requirement_id", r["id"])
            out.append({
                "requirement_id": int(rid) if rid is not None else None,
                "metadata": meta,
                "distance": r["distance"],
            })
        return out
    except Exception as e:
        logger.error("需求语义检索失败: %s", e, exc_info=True)
        raise RuntimeError("Milvus 需求语义检索失败") from e


def get_count() -> int:
    """获取向量库中供应商总数。

    集合不存在时返回 0；只有在 Milvus 真正不可用时抛出异常，
    让健康检查能够区分“没有数据”和“存储故障”。

    Raises:
        RuntimeError: Milvus 计数请求失败。
    """
    try:
        client = _get_client()
        if not client.has_collection(COLLECTION_NAME):
            return 0
        result = client.query(
            collection_name=COLLECTION_NAME,
            filter=f"{_PK_FIELD} >= 0",
            output_fields=["count(*)"],
            # 与检索一致：Strong 保证刚写入的画像立即计数可见
            consistency_level="Strong",
        )
        if result:
            return int(result[0].get("count(*)", 0))
        return 0
    except Exception as e:
        logger.error("Milvus 计数失败: %s", e, exc_info=True)
        raise RuntimeError("Milvus 供应商向量计数失败") from e


# ==================== AI 知识库文档分块集合操作 ====================

def upsert_chunks(chunks: list[dict]) -> None:
    """批量写入文档分块向量（dense 向量 + BM25 输入文本 + payload 元数据）。

    Args:
        chunks: [{"id": str, "vector": list[float], "text": str, "payload": dict}]，
                text 为 BM25 的输入（文件名 + 章节路径 + 正文），payload 至少含
                tenant_id / document_id / chunk_index / file_name。

    Raises:
        ValueError: 分块缺少 text（会让该块永远无法被 BM25 命中）。
        RuntimeError: Milvus 写入失败。
    """
    if not chunks:
        return
    _ensure_doc_collection()
    client = _get_client()
    data = []
    for c in chunks:
        payload = c["payload"] or {}
        text = (c.get("text") or "").strip()
        if not text:
            raise ValueError(f"分块缺少 text，无法建立 BM25 索引: id={c.get('id')}")
        data.append({
            _PK_FIELD: _id_to_int(c["id"]),  # 分块 UUID → int64
            _VECTOR_FIELD: c["vector"],
            _TEXT_FIELD: text[: _TEXT_MAX_CHARS],
            _TENANT_FIELD: int(payload.get("tenant_id") or 0),
            _META_FIELD: _dump_meta(payload),
            _DOC_ID_FIELD: _id_to_int(payload.get("document_id") or 0),
        })
    try:
        client.upsert(collection_name=DOC_COLLECTION_NAME, data=data)
        logger.info("文档分块向量写入成功: %d 条", len(data))
    except Exception as e:
        logger.error("文档分块向量写入失败: %s", e, exc_info=True)
        raise RuntimeError("文档分块向量写入失败") from e


def delete_by_document(document_id: str, tenant_id: int) -> None:
    """按文档ID + 租户删除该文档的全部分块向量。

    用 filter 表达式（tenant_id + doc_id 标量字段）批量删除，不依赖分块 ID 列表。
    """
    try:
        _ensure_doc_collection()
        client = _get_client()
        expr = f"{_TENANT_FIELD} == {int(tenant_id)} and {_DOC_ID_FIELD} == {_id_to_int(document_id)}"
        client.delete(collection_name=DOC_COLLECTION_NAME, filter=expr)
        logger.info("文档分块向量已删除: document_id=%s, tenant_id=%s", document_id, tenant_id)
    except Exception as e:
        logger.error("文档分块向量删除失败: %s", e, exc_info=True)
        raise RuntimeError("文档分块向量删除失败") from e


def _doc_row(payload: dict, chunk_id, score: float, source: str) -> dict:
    """把一条 Milvus 命中映射成检索结果行。

    Args:
        payload: metadata_json 反序列化后的业务字段
        chunk_id: Milvus 主键（uid），用于融合/保送时去重
        score: 该路检索分（hybrid=RRF 分，dense=余弦，BM25=BM25 分）
        source: 来源标记 hybrid / dense / keyword，供日志与排查

    Returns:
        统一结构的检索结果
    """
    return {
        "chunk_id": chunk_id,
        "document_id": payload.get("document_id", ""),
        "file_name": payload.get("file_name", ""),
        "chunk_index": payload.get("chunk_index", 0),
        "content": payload.get("content", ""),
        "score": score,
        # 结构元数据：供来源标注与引用展示章节（白名单必须透出，否则被剥离）
        "heading_path": payload.get("heading_path") or [],
        "chunk_type": payload.get("chunk_type", ""),
        "parent_id": payload.get("parent_id", ""),
        # hybrid=RRF 融合；dense=混合检索不可用的降级；keyword=BM25 精确命中保送
        "recall_source": source,
    }


def _map_raw_hits(results, source: str) -> list[dict]:
    """把 Milvus search/hybrid_search 的原始返回（list[list[hit]]）映射成结果行。"""
    out = []
    for hit in (results[0] if results else []):
        payload = _load_meta((hit.get("entity") or {}).get(_META_FIELD))
        out.append(_doc_row(payload, hit.get("id"), hit.get("distance", 0.0), source))
    return out


def _row_key(row: dict):
    """检索结果去重键：优先用 Milvus 主键，缺失时退回 (文档ID, 块序号)。"""
    chunk_id = row.get("chunk_id")
    if chunk_id is not None:
        return ("uid", chunk_id)
    return ("doc", row.get("document_id"), row.get("chunk_index"))


def _merge_keyword_guarantee(base: list[dict], keyword: list[dict],
                             top_k: int) -> list[dict]:
    """把 BM25 精确命中保送到候选窗口，再用融合结果补满 top_k。

    为什么需要：RRF 只按名次加权，一个只在关键词路排第一的块得分 1/(60+1)=0.0164，
    而一个两路都排第 50 的通用块得分 2/110=0.0182——精确命中反而被挤出前 20。
    这里把 BM25 前 N 名强制放在最前面（下游 rerank 名额仍是 20），保证编号、
    专有名词类查询不会被"两路都勉强命中"的块挤掉。

    Args:
        base: RRF 融合结果（或 hybrid 失败时的 dense 降级结果）
        keyword: BM25 单路精确命中（按分降序）
        top_k: 候选窗口大小（等于 rerank 名额）

    Returns:
        去重后的候选窗口，关键词保送项在前
    """
    if not keyword:
        return base[:top_k]
    out = []
    seen = set()
    # 1. 关键词精确命中优先占位
    for row in keyword:
        key = _row_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
        if len(out) >= top_k:
            return out
    # 2. 融合结果补满剩余名额（跳过已在保送里的块，避免重复占位）
    for row in base:
        key = _row_key(row)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
        if len(out) >= top_k:
            break
    return out


def query_chunks(query_text: str, query_embedding: list[float], tenant_id: int,
                 top_k: int = 5) -> list[dict]:
    """按租户过滤做 dense + BM25 混合检索，RRF 融合后返回候选。

    可用性策略：
      - hybrid_search 失败（BM25 索引异常/服务抖动）→ 退回 dense-only，不让整条
        知识库问答直接不可用；
      - 融合后额外取 BM25 前 N 名保送，避免精确编号/专有名词被 RRF 的
        「两路重合」权重挤出 rerank 名额。

    Args:
        query_text: 用户问题原文（BM25 分词输入，不能用向量代替）
        query_embedding: 查询向量（dense 路）
        tenant_id: 租户ID（两路都必须过滤，防跨租户知识泄露）
        top_k: 融合后返回条数（下游 rerank 名额）

    Returns:
        [{"chunk_id", "document_id", "file_name", "chunk_index", "content",
          "score", "recall_source", ...}]
        score：hybrid 为 RRF 融合分（不可当相似度阈值）；dense 降级为余弦；
        keyword 保送为 BM25 分。

    Raises:
        ValueError: 查询文本/向量为空或向量维度不匹配。
        RuntimeError: Milvus 检索失败（hybrid 与 dense 均不可用）。
    """
    if not query_text or not query_text.strip():
        raise ValueError("混合检索的问题文本不能为空")
    if not query_embedding:
        raise ValueError("语义检索向量不能为空")
    if len(query_embedding) != VECTOR_DIM:
        raise ValueError(f"语义检索向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(query_embedding)}")

    try:
        client = _get_client()
        filter_expr = f"{_TENANT_FIELD} == {int(tenant_id)}"
        output_fields = [_PK_FIELD, _META_FIELD, _TENANT_FIELD]

        # 1. 主路径：dense + BM25 双路 hybrid_search，RRF 融合
        try:
            _ensure_doc_collection()
            branch_limit = max(top_k, HYBRID_BRANCH_LIMIT)
            # 两路各自召回：dense 走语义，BM25 走关键词/编号精确匹配。
            # filter 必须逐路下——hybrid_search 没有顶层 filter 参数。
            reqs = [
                AnnSearchRequest(data=[query_embedding], anns_field=_VECTOR_FIELD,
                                 param={"metric_type": METRIC_TYPE},
                                 limit=branch_limit, expr=filter_expr),
                AnnSearchRequest(data=[query_text], anns_field=_SPARSE_FIELD,
                                 param={"metric_type": "BM25"},
                                 limit=branch_limit, expr=filter_expr),
            ]
            results = client.hybrid_search(
                collection_name=DOC_COLLECTION_NAME,
                reqs=reqs,
                ranker=RRFRanker(RRF_K),
                # 融合后交给 rerank 的候选数：调用方已按 RETRIEVE_PREFETCH 传入 20
                limit=top_k,
                output_fields=output_fields,
                # 与写入一致：刚解析完的文档立即可检索
                consistency_level="Strong",
            )
            base = _map_raw_hits(results, "hybrid")
        except Exception as e:
            # 混合检索不可用（BM25 索引/稀疏字段/服务抖动）→ 退回 dense 单路。
            # 使用 _search 而不是 hybrid：即便集合缺 BM25 字段也能继续服务。
            logger.warning("混合检索失败，降级 dense-only: %s", e)
            dense_rows = _search(DOC_COLLECTION_NAME, query_embedding, top_k, filter_expr)
            base = [
                _doc_row(r.get("metadata") or {}, r.get("id"), r.get("score", 0.0), "dense")
                for r in dense_rows
            ]

        # 2. 精确命中保送：单独取 BM25 前 N 名（失败静默跳过，不影响主结果）
        keyword = []
        if HYBRID_KEYWORD_GUARANTEE > 0:
            try:
                keyword_results = client.search(
                    collection_name=DOC_COLLECTION_NAME,
                    data=[query_text],
                    anns_field=_SPARSE_FIELD,
                    search_params={"metric_type": "BM25"},
                    limit=HYBRID_KEYWORD_GUARANTEE,
                    output_fields=output_fields,
                    filter=filter_expr,
                    consistency_level="Strong",
                )
                keyword = _map_raw_hits(keyword_results, "keyword")
            except Exception as e:
                logger.warning("关键词保送检索失败，跳过: %s", e)

        # 3. 保送项排前 + 融合结果补满 top_k
        merged = _merge_keyword_guarantee(base, keyword, top_k)
        logger.debug(
            "文档分块检索完成: top_k=%d 融合=%d 保送=%d 返回=%d 来源=%s",
            top_k, len(base), len(keyword), len(merged),
            sorted({r.get("recall_source") for r in merged}),
        )
        return merged
    except Exception as e:
        logger.error("文档分块检索失败: %s", e, exc_info=True)
        raise RuntimeError("Milvus 文档分块检索失败") from e
