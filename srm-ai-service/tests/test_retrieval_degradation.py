"""检索链路降级与关键词保送测试（P0）。

锁定三条可用性/正确性约束：
  1. hybrid_search 失败 → 退回 dense-only 检索，整条知识库问答不应直接不可用；
  2. rerank 失败/返回条数不符 → 回退 RRF 原序并打降级标记，不得返回空列表
     （返回空会被上层误判为「知识库没有相关资料」而拒答）；
  3. BM25 精确命中（编号/专有名词）在 RRF 融合后仍要保证进入 rerank 候选窗口，
     否则会被「两路都差点命中」的通用块挤掉。
"""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.rag import retrieve as rag_mod  # noqa: E402
from app.rag import rerank as rerank_mod  # noqa: E402
from app.store import milvus_store  # noqa: E402


# ==================== 测试替身 ====================

class FakeDocClient:
    """驱动 query_chunks 的 hybrid → dense 降级与 BM25 保送路径。"""

    def __init__(self, hybrid_result=None, hybrid_raises=False,
                 dense_result=None, bm25_result=None, bm25_raises=False):
        self.hybrid_result = hybrid_result or []
        self.hybrid_raises = hybrid_raises
        self.dense_result = dense_result or []
        self.bm25_result = bm25_result or []
        self.bm25_raises = bm25_raises
        self.calls = []

    def has_collection(self, name):
        return True

    def describe_collection(self, name):
        from pymilvus import DataType

        return {
            "fields": [
                {"name": "uid", "type": DataType.INT64},
                {"name": "vector", "type": DataType.FLOAT_VECTOR},
                {"name": "text", "type": DataType.VARCHAR},
                {"name": "sparse_vector", "type": DataType.SPARSE_FLOAT_VECTOR},
                {"name": "tenant_id", "type": DataType.INT64},
                {"name": "metadata_json", "type": DataType.VARCHAR},
                {"name": "doc_id", "type": DataType.INT64},
            ],
            "functions": [{
                "name": "doc_bm25", "type": 1,
                "input_field_names": "['text']",
                "output_field_names": "['sparse_vector']",
            }],
        }

    def hybrid_search(self, collection_name, reqs, ranker, limit, output_fields, **kwargs):
        self.calls.append(("hybrid", limit))
        if self.hybrid_raises:
            raise RuntimeError("hybrid search unavailable")
        return [self.hybrid_result]

    def search(self, collection_name, data, anns_field=None, search_params=None,
               limit=10, output_fields=None, **kwargs):
        # 知识库集合有 dense/sparse 两个向量字段，不指定 anns_field 会被 Milvus 拒绝
        if anns_field is None:
            raise RuntimeError("multiple anns_fields exist, please specify a anns_field")
        self.calls.append((anns_field, limit))
        if anns_field == "sparse_vector":
            if self.bm25_raises:
                raise RuntimeError("bm25 branch failed")
            return [self.bm25_result]
        return [self.dense_result]


def _hit(doc_id: str, chunk_index: int, score: float, content: str = "正文") -> dict:
    """构造一条 Milvus 命中（metadata_json 只放 query_chunks 会读的字段）。"""
    import json
    return {
        "id": abs(hash((doc_id, chunk_index))) % 10_000_000,
        "distance": score,
        "entity": {
            "tenant_id": 3,
            "metadata_json": json.dumps({
                "document_id": doc_id, "file_name": f"{doc_id}.pdf",
                "chunk_index": chunk_index, "content": content,
                "heading_path": [], "parent_id": f"p-{doc_id}",
            }, ensure_ascii=False),
        },
    }


@pytest.fixture
def patched_client(monkeypatch):
    """把 milvus_store 的客户端替换为可控替身。"""
    def install(client):
        monkeypatch.setattr(milvus_store, "_client", client)
        return client
    return install


# ==================== P0-2 rerank 降级 ====================

def _patch_rerank_call(monkey_behavior):
    """替换 rerank 的远程调用 + 确保 key/enabled 就绪。"""
    orig_call = rerank_mod._call_rerank
    orig_key = rerank_mod._API_KEY
    orig_enabled = rerank_mod.RERANK_ENABLED
    rerank_mod._API_KEY = "test-key"
    rerank_mod.RERANK_ENABLED = True
    rerank_mod._call_rerank = monkey_behavior
    return lambda: (
        setattr(rerank_mod, "_call_rerank", orig_call),
        setattr(rerank_mod, "_API_KEY", orig_key),
        setattr(rerank_mod, "RERANK_ENABLED", orig_enabled),
    )


def test_rerank_exception_falls_back_to_original_order():
    """交叉编码器异常 → 按召回原序返回全部候选，并标降级。"""
    def boom(q, docs):
        raise TimeoutError("simulated")

    restore = _patch_rerank_call(boom)
    try:
        cands = [{"content": "a"}, {"content": "b"}, {"content": "c"}]
        out = rerank_mod.rerank("q", cands, 5)
        assert [c["content"] for c in out] == ["a", "b", "c"], out
        assert all(c.get("rerank_degraded") is True for c in out), out
        assert all("rerank_score" not in c for c in out), "降级结果不应伪造分数"
    finally:
        restore()


def test_rerank_score_count_mismatch_falls_back():
    """返回分数条数与候选不符（上游异常）→ 同样回退原序，不丢结果。"""
    restore = _patch_rerank_call(lambda q, docs: [0.9])   # 3 个候选只给 1 个分
    try:
        cands = [{"content": "a"}, {"content": "b"}, {"content": "c"}]
        out = rerank_mod.rerank("q", cands, 5)
        assert len(out) == 3 and all(c.get("rerank_degraded") for c in out), out
    finally:
        restore()


def test_rerank_degraded_respects_top_n():
    """降级也要遵守 top_n 上限，不能把 20 条候选全灌进上下文。"""
    def boom(q, docs):
        raise TimeoutError("simulated")

    restore = _patch_rerank_call(boom)
    try:
        cands = [{"content": str(i)} for i in range(10)]
        out = rerank_mod.rerank("q", cands, 3)
        assert [c["content"] for c in out] == ["0", "1", "2"], out
    finally:
        restore()


def test_rag_rerank_keeps_candidates_when_rerank_raises(monkeypatch):
    """rag._rerank 层兜底：rerank 抛异常也要返回候选，不能返回空（否则误拒答）。"""
    def boom(q, cands, top_k):
        raise RuntimeError("rerank down")

    monkeypatch.setattr(rerank_mod, "rerank", boom)
    cands = [{"content": "a", "parent_id": "p1"}, {"content": "b", "parent_id": "p2"}]
    out = rag_mod._rerank("q", cands, 2)
    assert [c["content"] for c in out] == ["a", "b"], out
    assert all(c.get("rerank_degraded") for c in out)


# ==================== P0-1 hybrid 降级 ====================

def test_query_chunks_falls_back_to_dense_when_hybrid_fails(patched_client):
    """hybrid_search 抛错 → 退回 dense 单路，结果仍可用。"""
    client = patched_client(FakeDocClient(
        hybrid_raises=True,
        dense_result=[_hit("101", 0, 0.8)],
        bm25_result=[],
    ))
    rows = milvus_store.query_chunks("准入要求", [0.1] * 1024, tenant_id=3, top_k=20)
    assert len(rows) == 1 and rows[0]["document_id"] == "101", rows
    assert rows[0]["recall_source"] == "dense", "降级结果应标注来源"
    assert any(c[0] == "vector" for c in client.calls), "必须真的走了 dense 检索"


def test_query_chunks_raises_when_dense_fallback_also_fails(patched_client):
    """两路都不可用才允许抛错（交给上层返回错误，而不是假成功）。"""
    class Broken(FakeDocClient):
        def search(self, *a, **k):
            raise RuntimeError("milvus down")

    patched_client(Broken(hybrid_raises=True))
    with pytest.raises(RuntimeError):
        milvus_store.query_chunks("q", [0.1] * 1024, tenant_id=3, top_k=20)


# ==================== P0-3 BM25 精确命中保送 ====================

def test_keyword_hits_are_forced_into_candidate_window(patched_client):
    """BM25 精确命中若不进 RRF 前 20，也要强制补进候选窗口（rerank 名额=20）。"""
    fused = [_hit("docA", i, 0.03 - i * 0.001) for i in range(20)]
    keyword = [_hit("docX", 0, 9.5, content="HT-2024-001 违约金条款")]  # 只在关键词路命中

    patched_client(FakeDocClient(
        hybrid_result=fused, dense_result=[], bm25_result=keyword,
    ))
    rows = milvus_store.query_chunks("HT-2024-001", [0.1] * 1024, tenant_id=3, top_k=20)

    assert len(rows) == 20, f"候选窗口应仍为 20 条: {len(rows)}"
    assert any(r["document_id"] == "docX" for r in rows), "关键词精确命中必须保送进候选窗口"
    # 保送项放在最前，保证下游任何截断/无 rerank 场景都不会把它丢掉
    assert rows[0]["recall_source"] == "keyword", "保送项应排在最前并标注来源"


def test_keyword_hits_do_not_duplicate_fused_hits(patched_client):
    """BM25 保送项如果已在融合结果里，不能重复占名额。"""
    fused = [_hit("docA", i, 0.03) for i in range(5)]
    # 保送结果与融合结果同一个 document_id + chunk_index
    keyword = [_hit("docA", 0, 9.5), _hit("docX", 1, 8.0)]

    patched_client(FakeDocClient(
        hybrid_result=fused, dense_result=[], bm25_result=keyword,
    ))
    rows = milvus_store.query_chunks("q", [0.1] * 1024, tenant_id=3, top_k=20)

    keys = [(r["document_id"], r["chunk_index"]) for r in rows]
    assert len(keys) == len(set(keys)), f"候选不应重复: {keys}"
    assert len(rows) == 6, f"5 条融合 + 1 条新增保送 = 6: {len(rows)}"


def test_keyword_boost_failure_does_not_break_retrieval(patched_client):
    """保送用的 BM25 单路查询失败时，应静默跳过，不影响已融合的结果。"""
    patched_client(FakeDocClient(
        hybrid_result=[_hit("docA", 0, 0.03)],
        dense_result=[],
        bm25_raises=True,
    ))
    rows = milvus_store.query_chunks("q", [0.1] * 1024, tenant_id=3, top_k=20)
    assert len(rows) == 1 and rows[0]["recall_source"] == "hybrid", rows


def test_keyword_boost_respects_tenant_filter(patched_client):
    """保送检索同样必须带租户过滤，防止跨租户文档被强制注入。"""
    client = patched_client(FakeDocClient(
        hybrid_result=[], dense_result=[], bm25_result=[],
    ))
    milvus_store.query_chunks("q", [0.1] * 1024, tenant_id=7, top_k=20)
    # 检查 sparse 单路检索的 filter 表达式
    captured = {}
    orig = client.search

    def spy(collection_name, data, anns_field, search_params, limit,
            output_fields, filter=None, **kwargs):
        captured[anns_field] = filter
        return orig(collection_name, data, anns_field, search_params, limit,
                    output_fields, filter=filter, **kwargs)

    client.search = spy
    milvus_store.query_chunks("q", [0.1] * 1024, tenant_id=7, top_k=20)
    assert captured.get("sparse_vector") == "tenant_id == 7", captured
