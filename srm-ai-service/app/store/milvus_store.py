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

from pymilvus import DataType, MilvusClient  # Milvus 2.x 客户端 + 字段类型

logger = logging.getLogger("srm-ai.milvus")

# ==================== 配置 ====================

COLLECTION_NAME = "supplier_profiles"
# 公开采购需求集合（找需求语义召回，与画像隔离）
REQ_COLLECTION_NAME = "open_requirements"
# AI 知识库文档分块集合（与 supplier_profiles 隔离，互不污染）
DOC_COLLECTION_NAME = "ai_doc_chunks"

# Milvus 连接地址 — 默认指向内网 Milvus，可通过环境变量覆盖
MILVUS_HOST = os.environ.get("MILVUS_HOST", "120.77.169.224")
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


def _create_collection(name: str, with_doc_id: bool = False) -> None:
    """按统一 schema 创建集合：int64 主键 + 向量 + tenant_id 标量 + 动态元数据。

    with_doc_id=True 时额外声明 doc_id 标量字段（知识库分块按文档删除需要）。
    enable_dynamic_field=True 让未声明的键也可写入，但本模块只显式写受控字段。
    """
    client = _get_client()
    schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=True)
    schema.add_field(_PK_FIELD, DataType.INT64, is_primary=True)
    schema.add_field(_VECTOR_FIELD, DataType.FLOAT_VECTOR, dim=VECTOR_DIM)
    schema.add_field(_TENANT_FIELD, DataType.INT64)
    schema.add_field(_META_FIELD, DataType.VARCHAR, max_length=65535)
    if with_doc_id:
        schema.add_field(_DOC_ID_FIELD, DataType.INT64)

    index_params = client.prepare_index_params()
    index_params.add_index(field_name=_VECTOR_FIELD, index_type="AUTOINDEX", metric_type=METRIC_TYPE)
    # 标量过滤字段建 INVERTED 索引：tenant_id 用于检索/删除过滤，doc_id 用于按文档
    # 批量删除。Milvus 对标量字段无索引时会退化为段内扫描，数据量大时拖慢检索。
    index_params.add_index(field_name=_TENANT_FIELD, index_type="INVERTED")
    if with_doc_id:
        index_params.add_index(field_name=_DOC_ID_FIELD, index_type="INVERTED")

    client.create_collection(collection_name=name, schema=schema, index_params=index_params)
    logger.info("Milvus 集合已创建: %s, dim=%d, metric=%s", name, VECTOR_DIM, METRIC_TYPE)


def _expected_fields(with_doc_id: bool = False) -> dict:
    """当前代码期望的集合字段契约（字段名 → Milvus 类型）。

    与 _create_collection 的 schema 一一对应，供 _assert_schema 比对。
    """
    expected = {
        _PK_FIELD: DataType.INT64,
        _VECTOR_FIELD: DataType.FLOAT_VECTOR,
        _TENANT_FIELD: DataType.INT64,
        _META_FIELD: DataType.VARCHAR,
    }
    if with_doc_id:
        expected[_DOC_ID_FIELD] = DataType.INT64
    return expected


def _assert_schema(name: str, with_doc_id: bool = False) -> None:
    """校验已存在集合的 schema 与当前代码期望一致。

    集合存在但字段名/类型对不上（典型：由旧版本代码创建，如主键 id + metadata JSON）
    时直接报错——否则写入会抛晦涩的 DataNotMatchException，且被 Java 侧 catch-all
    静默吞掉，表现为「画像在 MySQL 保存成功、但向量库一直为空」。

    Raises:
        RuntimeError: 字段缺失或类型不符，需先删除旧集合再由本服务重建。
    """
    client = _get_client()
    fields = client.describe_collection(name)["fields"]
    actual = {f["name"]: f["type"] for f in fields}
    expected = _expected_fields(with_doc_id)
    mismatched = [n for n, t in expected.items() if actual.get(n) != t]
    if mismatched:
        raise RuntimeError(
            f"Milvus 集合 {name} 的 schema 与当前代码不匹配，"
            f"以下字段缺失或类型不符: {', '.join(mismatched)}；"
            f"实际字段: {', '.join(sorted(actual))}。"
            f"该集合由旧版本创建，需先删除后由本服务按新 schema 重建。"
        )


def _ensure_collection(name: str, with_doc_id: bool = False) -> None:
    """确保集合存在，且 schema 与当前代码一致。

    Raises:
        RuntimeError: Milvus 不可用、集合初始化失败，或已存在集合 schema 不匹配。
    """
    try:
        client = _get_client()
        if client.has_collection(name):
            # 已存在：校验 schema。旧版本遗留集合字段对不上时立刻报错，
            # 避免写入阶段才抛晦涩异常、且被上层静默吞掉。
            _assert_schema(name, with_doc_id=with_doc_id)
        else:
            _create_collection(name, with_doc_id=with_doc_id)
    except RuntimeError:
        raise  # schema 不匹配已是明确错误，保留原始信息与堆栈
    except Exception as e:
        logger.error("Milvus 集合初始化失败: %s, %s", name, e, exc_info=True)
        raise RuntimeError(f"Milvus 集合初始化失败: {name}") from e


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
    """批量写入文档分块向量（每个分块含 id/vector/payload 元数据）。

    Args:
        chunks: [{"id": str, "vector": list[float], "payload": dict}]，payload 至少含
                tenant_id / document_id / chunk_index / file_name。

    Raises:
        RuntimeError: Milvus 写入失败。
    """
    if not chunks:
        return
    _ensure_collection(DOC_COLLECTION_NAME, with_doc_id=True)
    client = _get_client()
    data = []
    for c in chunks:
        payload = c["payload"] or {}
        data.append({
            _PK_FIELD: _id_to_int(c["id"]),  # 分块 UUID → int64
            _VECTOR_FIELD: c["vector"],
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
        _ensure_collection(DOC_COLLECTION_NAME, with_doc_id=True)
        client = _get_client()
        expr = f"{_TENANT_FIELD} == {int(tenant_id)} and {_DOC_ID_FIELD} == {_id_to_int(document_id)}"
        client.delete(collection_name=DOC_COLLECTION_NAME, filter=expr)
        logger.info("文档分块向量已删除: document_id=%s, tenant_id=%s", document_id, tenant_id)
    except Exception as e:
        logger.error("文档分块向量删除失败: %s", e, exc_info=True)
        raise RuntimeError("文档分块向量删除失败") from e


def query_chunks(query_embedding: list[float], tenant_id: int, top_k: int = 5) -> list[dict]:
    """按租户过滤检索最相似的文档分块。

    Args:
        query_embedding: 查询向量
        tenant_id: 租户ID（filter 过滤，防跨租户知识泄露）
        top_k: 返回块数

    Returns:
        [{"document_id", "file_name", "chunk_index", "content", "score", ...}]

    Raises:
        ValueError: 查询向量为空或维度不匹配。
        RuntimeError: Milvus 检索失败。
    """
    if not query_embedding:
        raise ValueError("语义检索向量不能为空")
    if len(query_embedding) != VECTOR_DIM:
        raise ValueError(f"语义检索向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(query_embedding)}")

    try:
        _ensure_collection(DOC_COLLECTION_NAME)
        filter_expr = f"{_TENANT_FIELD} == {int(tenant_id)}"
        rows = _search(DOC_COLLECTION_NAME, query_embedding, top_k, filter_expr)
        out = []
        for r in rows:
            payload = r["metadata"]
            out.append({
                "document_id": payload.get("document_id", ""),
                "file_name": payload.get("file_name", ""),
                "chunk_index": payload.get("chunk_index", 0),
                "content": payload.get("content", ""),
                "score": r["score"],
                # 结构元数据：供来源标注与引用展示章节（白名单必须透出，否则被剥离）
                "heading_path": payload.get("heading_path") or [],
                "chunk_type": payload.get("chunk_type", ""),
                "parent_id": payload.get("parent_id", ""),
            })
        logger.debug("文档分块检索完成: top_k=%d, 返回 %d 条", top_k, len(out))
        return out
    except Exception as e:
        logger.error("文档分块检索失败: %s", e, exc_info=True)
        raise RuntimeError("Milvus 文档分块检索失败") from e
