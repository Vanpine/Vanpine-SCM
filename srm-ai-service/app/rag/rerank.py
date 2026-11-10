"""
重排（rerank）服务

用交叉编码器（cross-encoder）对初召回候选重排：dense 双塔召回便宜但粗，
rerank 用 query+doc 联合注意力精排，是「短问句 vs 长条款」最对症的杠杆。

走硅基流动 /v1/rerank（bge-reranker-v2-m3），复用 EMBEDDING_API_KEY。
关键：精排是「增强」不是「闸门」——超时/失败/返回异常时回退召回原序并标记
rerank_degraded，不能返回空列表；返回空会被上层误判为「知识库没有相关资料」
而直接拒答，用户明明有资料却得到"未找到依据"。
"""
import logging
import os

import httpx

logger = logging.getLogger("srm-ai.rerank")

# 开关（默认开启，缺失 key 时自动跳过）
RERANK_ENABLED = os.environ.get("RERANK_ENABLED", "true").lower() in ("1", "true", "yes")
# 重排模型
RERANK_MODEL = os.environ.get("RERANK_MODEL", "BAAI/bge-reranker-v2-m3")
# 超时（秒）：短，失败即回退召回原序（标记 degraded），不拖垮问答
RERANK_TIMEOUT = float(os.environ.get("RERANK_TIMEOUT", "8"))
# 复用 embedding 的 API 配置
_BASE_URL = os.environ.get("EMBEDDING_BASE_URL", "https://api.siliconflow.cn/v1")
_API_KEY = os.environ.get("EMBEDDING_API_KEY", "")
# 共享 HTTP 客户端：复用连接，避免每次重排都新建 TCP/TLS
_client = httpx.Client(limits=httpx.Limits(max_connections=16, max_keepalive_connections=8))
# 送入重排的候选上限（过多拖慢）
MAX_CANDIDATES = int(os.environ.get("RERANK_MAX_CANDIDATES", "20"))
# 置信度阈值：低于此分的块视为弱相关，丢弃不注入 Prompt（避免污染回答）。
# 注意：bge-reranker-v2-m3（硅基流动）的分数量级偏低——真实相关查询常在 0.1~0.2，
# 0.3 会把所有命中一刀切空（实测 query='简历' 最高仅 0.099）。取 0.05 + 下方保底 top1。
RERANK_SCORE_THRESHOLD = float(os.environ.get("RERANK_SCORE_THRESHOLD", "0.05"))


def _degraded(candidates: list[dict], top_n: int) -> list[dict]:
    """精排不可用时的降级结果：按召回原序返回 top_n，并标记 rerank_degraded。

    为什么不返回空：上层（rag.retrieve → tools.search_knowledge_base）把空结果
    当作「知识库没有相关资料」，会让模型拒答。精排服务抖动不应该等于没有资料，
    召回原序虽不如精排准，但至少是相关候选。

    为什么不伪造 rerank_score：下游按 rerank_score 做阈值过滤/观测，
    编一个分数会污染日志和门禁判断；降级用独立字段标识。

    Args:
        candidates: 召回原序候选（dense/BM25 融合后）
        top_n: 返回条数上限

    Returns:
        带 rerank_degraded=True 标记的候选副本
    """
    out = []
    for cand in candidates[:top_n]:
        item = dict(cand)
        item["rerank_degraded"] = True
        out.append(item)
    return out


def rerank(query: str, candidates: list[dict], top_n: int) -> list[dict]:
    """对候选块按与 query 的相关性重排，返回前 top_n 条（并过滤低置信块）。

    置信度门禁：分数低于 RERANK_SCORE_THRESHOLD 的候选直接丢弃。
    失败（超时/网络/格式错）时回退召回原序并标记 rerank_degraded——精排是增强层，
    不可用不应导致整条知识问答链路被判「无资料」。

    Args:
        query: 用户提问
        candidates: [{"content", ...}] 初召回候选
        top_n: 返回条数上限

    Returns:
        重排后、过阈值的 top_n 条（每条附 rerank_score）；
        未启用/无 key 时按原序返回 top_n；调用失败时按原序返回并标 rerank_degraded。
    """
    if not candidates:
        return []
    if not RERANK_ENABLED or not _API_KEY or not query.strip():
        # 未启用/未配置：不重排也不过滤，退回原序前 top_n（此时无分数可判，保持旧行为）
        return candidates[:top_n]

    docs = [c.get("content", "") for c in candidates[:MAX_CANDIDATES]]
    try:
        scores = _call_rerank(query, docs)
    except Exception as e:
        logger.warning("rerank 失败，回退召回原序(top_n=%d): %s", top_n, e)
        return _degraded(candidates, top_n)

    if len(scores) != len(docs):
        logger.warning("rerank 返回条数不符(%d vs %d)，回退召回原序", len(scores), len(docs))
        return _degraded(candidates, top_n)

    ranked = sorted(zip(candidates[:MAX_CANDIDATES], scores),
                    key=lambda p: p[1], reverse=True)
    out = []
    for cand, score in ranked[:top_n]:
        # 置信度门禁：低于阈值的块丢弃，不注入 Prompt
        if score < RERANK_SCORE_THRESHOLD:
            continue
        item = dict(cand)
        item["rerank_score"] = score
        out.append(item)
    # 兜底保底：过滤后为空但确有候选（且最高分不为 0）时，保留最高分那条。
    # 避免「明明召回到了、只是分数没过硬阈值」被一律判为"知识库没有"——bge-reranker
    # 的分数量级偏低（相关查询常在 0.1~0.2），硬阈值易误杀。宁滥勿缺。
    if not out and ranked and ranked[0][1] > 0:
        top_cand, top_score = ranked[0]
        item = dict(top_cand)
        item["rerank_score"] = top_score
        out.append(item)
    return out


def _call_rerank(query: str, docs: list[str]) -> list[float]:
    """调 /v1/rerank，返回与 docs 等长的相关性分数（0..1）。"""
    url = f"{_BASE_URL.rstrip('/')}/rerank"
    response = _client.post(
        url,
        json={
            "model": RERANK_MODEL,
            "query": query,
            "documents": docs,
            "top_n": len(docs),
        },
        headers={"Authorization": f"Bearer {_API_KEY}"},
        timeout=RERANK_TIMEOUT,
    )
    response.raise_for_status()
    body = response.json()
    # 硅基流动返回 {results: [{index, relevance_score}]}
    results = body.get("results") or []
    scores = [0.0] * len(docs)
    for r in results:
        idx = r.get("index")
        if isinstance(idx, int) and 0 <= idx < len(docs):
            scores[idx] = float(r.get("relevance_score", 0.0))
    return scores
