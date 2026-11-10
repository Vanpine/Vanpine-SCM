"""删除某文档在 Milvus 中的全部知识库向量（新旧集合都清）。

Java 的 `DELETE /api/admin/ai/documents/{id}` 会自动完成
「逻辑删文档 + 删父块 + 删向量 + 删 OSS」四件事，**优先用接口/页面删除**。
本脚本仅用于绕过 Java 手工清理（例如切换集合后清理重复文档的残留向量）。

用法：
    .venv/bin/python scripts/delete_document_vectors.py <tenant_id> <document_id> --dry-run
    .venv/bin/python scripts/delete_document_vectors.py <tenant_id> <document_id>
"""
import argparse
import os
import sys
from pathlib import Path

# 允许从仓库根目录直接运行（scripts/ 的上一级即 srm-ai-service）
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from dotenv import load_dotenv  # noqa: E402

load_dotenv()

from pymilvus import MilvusClient  # noqa: E402

from app.store.milvus_store import _id_to_int  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="按租户+文档删除 Milvus 中的知识库分块向量")
    parser.add_argument("tenant_id", type=int, help="租户 ID")
    parser.add_argument("document_id", type=int, help="文档 ID（ai_documents.id）")
    parser.add_argument("--dry-run", action="store_true", help="只统计，不执行删除")
    args = parser.parse_args()

    host = os.environ["MILVUS_HOST"]
    port = os.environ.get("MILVUS_PORT", "19530")
    client = MilvusClient(uri=f"http://{host}:{port}", timeout=10)

    # 文档 ID 在 Milvus 里是 int64 哈希（doc_id 字段）
    doc_pk = _id_to_int(args.document_id)
    expr = f"tenant_id == {args.tenant_id} and doc_id == {doc_pk}"

    # 新集合（混合检索）+ 旧集合（dense-only）都清，避免残留向量仍被召回
    collections = list(dict.fromkeys([
        os.environ.get("DOC_COLLECTION_NAME", "ai_doc_chunks_v2"),
        "ai_doc_chunks",
    ]))

    for name in collections:
        if not client.has_collection(name):
            print(f"[skip] {name}: 集合不存在")
            continue
        rows = client.query(
            collection_name=name, filter=expr,
            output_fields=["count(*)"], consistency_level="Strong",
        )
        count = int(rows[0].get("count(*)", 0)) if rows else 0
        print(f"[{'dry-run' if args.dry_run else 'delete'}] {name}: {count} 条")
        if count and not args.dry_run:
            client.delete(collection_name=name, filter=expr)

    if args.dry_run:
        print("以上为 dry-run，未做任何修改。")
    else:
        print("向量删除请求已提交（Milvus 异步生效，通常数秒内）。")


if __name__ == "__main__":
    main()
