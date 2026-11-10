"""Milvus 原生 BM25 混合检索测试。

锁定行为：
  1. 文档集合 schema 必须含 text / sparse_vector 字段，且声明 BM25 Function；
  2. upsert 必须携带 text（BM25 的输入），缺失即报错（不能静默写空）；
  3. 检索走 dense + sparse 两路 hybrid_search，RRF 融合，两路都带 tenant 过滤；
  4. 解析入库的 text 包含文件名 / 章节路径 / 正文；
  5. retrieve 把原始 query 文本传给混合检索（BM25 不能拿向量查）。
"""
import pytest
from pymilvus import DataType

from app.rag import retrieve as rag_mod
from app.document.chunker import Block
from app.store import milvus_store


DOC_FIELDS = [
    {"name": "uid", "type": DataType.INT64, "is_primary": True},
    {"name": "vector", "type": DataType.FLOAT_VECTOR, "params": {"dim": 1024}},
    {"name": "text", "type": DataType.VARCHAR, "params": {"max_length": 8192}},
    {"name": "sparse_vector", "type": DataType.SPARSE_FLOAT_VECTOR},
    {"name": "tenant_id", "type": DataType.INT64},
    {"name": "metadata_json", "type": DataType.VARCHAR},
    {"name": "doc_id", "type": DataType.INT64},
]
BM25_FUNC = [{
    "name": "doc_bm25",
    "type": 1,
    "input_field_names": "['text']",
    "output_field_names": "['sparse_vector']",
}]


class FakeClient:
    """只驱动 schema 守卫 / upsert / hybrid_search 三条路径。"""

    def __init__(self, fields=None, functions=None):
        self.fields = DOC_FIELDS if fields is None else fields
        self.functions = BM25_FUNC if functions is None else functions
        self.upserted = None
        self.hybrid_calls = []
        self.created = []

    def has_collection(self, name):
        return True

    def describe_collection(self, name):
        return {"fields": self.fields, "functions": self.functions}

    def upsert(self, collection_name, data):
        self.upserted = data

    def delete(self, collection_name, filter):
        return None

    def hybrid_search(self, collection_name, reqs, ranker, limit,
                      output_fields, **kwargs):
        self.hybrid_calls.append({
            "collection_name": collection_name, "reqs": reqs, "ranker": ranker,
            "limit": limit, "output_fields": output_fields, **kwargs,
        })
        return [[{
            "id": 7,
            "distance": 0.032,
            "entity": {
                "tenant_id": 3,
                "metadata_json": (
                    '{"document_id":"9","file_name":"制度.pdf","chunk_index":2,'
                    '"content":"正文","heading_path":["准入"],"parent_id":"p1"}'
                ),
            },
        }]]


# ==================== schema 守卫 ====================

def test_doc_collection_requires_bm25_function(monkeypatch):
    """集合缺 BM25 Function 必须报错，而不是等到写入时抛晦涩异常。"""
    monkeypatch.setattr(milvus_store, "_client", FakeClient(functions=[]))
    with pytest.raises(RuntimeError, match="BM25"):
        milvus_store._ensure_doc_collection()


def test_doc_collection_requires_text_and_sparse_fields(monkeypatch):
    """旧集合只有 dense/tenant/metadata，必须明确提示缺字段。"""
    fields = [f for f in DOC_FIELDS if f["name"] not in ("text", "sparse_vector")]
    monkeypatch.setattr(milvus_store, "_client", FakeClient(fields=fields))
    with pytest.raises(RuntimeError, match="text|sparse_vector"):
        milvus_store._ensure_doc_collection()


# ==================== 写入 ====================

def test_upsert_chunks_sends_bm25_text(monkeypatch):
    """text 是 BM25 的唯一输入，必须随点一起写入。"""
    fake = FakeClient()
    monkeypatch.setattr(milvus_store, "_client", fake)
    milvus_store.upsert_chunks([{
        "id": "doc-1-chunk-0",
        "vector": [0.1] * 1024,
        "text": "制度.pdf > 准入\n需提供营业执照",
        "payload": {"tenant_id": 3, "document_id": 9, "chunk_index": 0,
                    "file_name": "制度.pdf", "content": "需提供营业执照"},
    }])
    assert fake.upserted[0]["text"] == "制度.pdf > 准入\n需提供营业执照"
    assert fake.upserted[0]["tenant_id"] == 3
    assert fake.upserted[0]["doc_id"] == 9


def test_upsert_chunks_rejects_missing_text(monkeypatch):
    """缺 text 不能写入空字符串——否则该块永远无法被 BM25 命中。"""
    monkeypatch.setattr(milvus_store, "_client", FakeClient())
    with pytest.raises(ValueError, match="text"):
        milvus_store.upsert_chunks([{
            "id": "doc-1-chunk-0",
            "vector": [0.1] * 1024,
            "payload": {"tenant_id": 3, "document_id": 9, "content": "正文"},
        }])


# ==================== 检索 ====================

def test_query_chunks_runs_two_way_hybrid_search(monkeypatch):
    """dense + BM25 两路召回，RRF 融合，两路都按租户过滤。"""
    fake = FakeClient()
    monkeypatch.setattr(milvus_store, "_client", fake)

    rows = milvus_store.query_chunks("准入要求", [0.1] * 1024, tenant_id=3, top_k=20)

    call = fake.hybrid_calls[0]
    assert call["collection_name"] == milvus_store.DOC_COLLECTION_NAME
    assert len(call["reqs"]) == 2
    by_field = {r.anns_field: r for r in call["reqs"]}
    assert set(by_field) == {"vector", "sparse_vector"}
    # 向量路：query 向量 + COSINE；关键词路：query 原文 + BM25
    assert by_field["vector"].data == [[0.1] * 1024]
    assert by_field["vector"].param["metric_type"] == "COSINE"
    assert by_field["sparse_vector"].data == ["准入要求"]
    assert by_field["sparse_vector"].param["metric_type"] == "BM25"
    # 两路都带租户过滤
    for req in call["reqs"]:
        assert req.expr == "tenant_id == 3"
    # RRF 融合
    assert vars(call["ranker"])["_strategy"] == "rrf"
    # 结果映射保留引用定位字段
    assert rows[0]["file_name"] == "制度.pdf"
    assert rows[0]["parent_id"] == "p1"
    assert rows[0]["heading_path"] == ["准入"]


def test_retrieve_passes_raw_query_to_hybrid(monkeypatch):
    """BM25 需要原始 query 文本，retrieve 不能只传向量。"""
    seen = {}

    def fake_query_chunks(query_text, query_embedding, tenant_id, top_k):
        seen.update(query_text=query_text, tenant_id=tenant_id, top_k=top_k)
        return []

    monkeypatch.setattr(rag_mod, "encode", lambda q: [0.1] * 8)
    monkeypatch.setattr(rag_mod, "query_chunks", fake_query_chunks)

    rag_mod.retrieve("准入要求", tenant_id=3)

    assert seen["query_text"] == "准入要求"
    assert seen["tenant_id"] == 3


# ==================== 解析入库的 text ====================

def test_parse_and_index_text_contains_document_section_and_body(monkeypatch):
    """BM25 的 text 要带文件名 + 章节路径，正文之外也能按文档名/章节名命中。"""
    captured = {}

    def fake_upsert(points):
        captured["points"] = points

    monkeypatch.setattr(rag_mod, "_download", lambda url: b"raw")
    monkeypatch.setattr(rag_mod, "_extract_blocks", lambda raw, ft: [
        Block(text="第一章 准入", kind="heading", level=1,
              heading_path=["第一章 准入"]),
        Block(text="需提供营业执照与资质证明", kind="para",
              heading_path=["第一章 准入"]),
    ])
    monkeypatch.setattr(rag_mod, "encode_batch",
                        lambda texts: [[0.1] * 8 for _ in texts])
    monkeypatch.setattr(rag_mod, "upsert_chunks", fake_upsert)
    monkeypatch.setattr(milvus_store, "delete_by_document", lambda *a, **k: None)

    count, parents = rag_mod.parse_and_index(
        document_id=9, tenant_id=3, oss_url="http://oss/x",
        file_name="制度.pdf", file_type="txt")

    assert count == len(captured["points"]) >= 1
    text = captured["points"][0]["text"]
    assert "制度.pdf" in text
    assert "第一章 准入" in text
    assert "需提供营业执照与资质证明" in text
    # 正文仍原样进 payload，供引用展示
    assert "需提供营业执照与资质证明" in captured["points"][0]["payload"]["content"]
