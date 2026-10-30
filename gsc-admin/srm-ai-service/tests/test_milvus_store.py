"""Milvus 集合 schema 守卫测试。

回归背景：远端 Milvus 上的 supplier_profiles 是旧版代码（主键 id + metadata JSON）
建的，与当前 milvus_store.py（uid + metadata_json）字段对不上。_ensure_collection
只判断 has_collection，集合存在就直接复用，于是每次写入都被 Milvus 拒绝
（unexpected field `uid`），且异常被 Java 侧静默吞掉，向量库长期为空。
本测试锁定：遇到陈旧 schema 必须显式报错，不允许静默通过。
"""
import pytest
from pymilvus import DataType

from app.store import milvus_store


class FakeMilvusClient:
    """只需要 has_collection / describe_collection 两个方法驱动守卫分支。"""

    def __init__(self, fields):
        self._fields = fields

    def has_collection(self, name):
        return True

    def describe_collection(self, name):
        return {"fields": self._fields}


def test_ensure_collection_raises_on_legacy_schema(monkeypatch):
    """旧版本集合（id/metadata）必须抛错，并提示需删除后重建。"""
    legacy = [
        {"name": "id", "type": DataType.INT64},
        {"name": "vector", "type": DataType.FLOAT_VECTOR},
        {"name": "tenant_id", "type": DataType.INT64},
        {"name": "metadata", "type": DataType.JSON},
    ]
    monkeypatch.setattr(milvus_store, "_client", FakeMilvusClient(legacy))

    with pytest.raises(RuntimeError, match="schema 与当前代码不匹配"):
        milvus_store._ensure_collection(milvus_store.COLLECTION_NAME)


def test_ensure_collection_raises_when_doc_id_missing(monkeypatch):
    """知识库集合缺 doc_id 也要报错（旧 schema 用 document_id）。"""
    no_doc_id = [
        {"name": "uid", "type": DataType.INT64},
        {"name": "vector", "type": DataType.FLOAT_VECTOR},
        {"name": "tenant_id", "type": DataType.INT64},
        {"name": "metadata_json", "type": DataType.VARCHAR},
    ]
    monkeypatch.setattr(milvus_store, "_client", FakeMilvusClient(no_doc_id))

    with pytest.raises(RuntimeError, match="doc_id"):
        milvus_store._ensure_collection(milvus_store.DOC_COLLECTION_NAME, with_doc_id=True)


def test_ensure_collection_passes_on_matching_schema(monkeypatch):
    """schema 与代码一致时不抛异常。"""
    ok = [
        {"name": "uid", "type": DataType.INT64},
        {"name": "vector", "type": DataType.FLOAT_VECTOR},
        {"name": "tenant_id", "type": DataType.INT64},
        {"name": "metadata_json", "type": DataType.VARCHAR},
    ]
    monkeypatch.setattr(milvus_store, "_client", FakeMilvusClient(ok))

    milvus_store._ensure_collection(milvus_store.COLLECTION_NAME)  # 不抛即通过
