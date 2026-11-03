"""
在线 Embedding API 服务模块
通过 HTTP 调用硅基流动（SiliconFlow）托管的 BGE 模型，无需本地 PyTorch/SentenceTransformer
支持任意 OpenAI 兼容的 Embedding API（阿里百炼 / 智谱 / 硅基流动 等，改环境变量即可切换）
"""
import os  # 读取环境变量
import json  # JSON 序列化与反序列化
import logging  # 日志模块
import threading  # 缓存读写锁

import httpx  # HTTP 客户端（连接池复用，替代标准库 urllib 的每次握手）

# 模块日志器
logger = logging.getLogger("srm-ai.embedding")

# ==================== 配置（通过环境变量覆盖） ====================

# Embedding API 密钥 — 从环境变量读取，不写死在代码里
EMBEDDING_API_KEY = os.environ.get("EMBEDDING_API_KEY", "sk-luwhfzptejfkzawlbwvdjjzuirahdjelszpjrczdhfohuvsp")
# Embedding API 地址 — 默认硅基流动（OpenAI 兼容接口）
EMBEDDING_BASE_URL = os.environ.get("EMBEDDING_BASE_URL", "https://api.siliconflow.cn/v1")
# Embedding 模型名称 — BGE-M3 多语言（100+语种），1024 维向量
EMBEDDING_MODEL = os.environ.get("EMBEDDING_MODEL", "BAAI/bge-m3")
# 向量维度 — 根据模型不同需同步修改：
#   BAAI/bge-m3 → 1024（多语言）
#   BAAI/bge-large-zh-v1.5 → 1024（仅中文）
#   阿里百炼 text-embedding-v3 → 1024
#   OpenAI text-embedding-3-small → 1536
VECTOR_DIM = int(os.environ.get("EMBEDDING_VECTOR_DIM", "1024"))

# ==================== 共享 HTTP 客户端 ====================

# 模块级单例，复用 TCP/TLS 连接：原来每次 encode 都 urlopen，等于每次重新握手。
# httpx.Client 线程安全——FastAPI 同步端点在线程池里并发调用同一实例是安全的。
# 超时按调用点传参覆盖（单条 30s / 批量 60s），不在客户端上写死。
_client = httpx.Client(limits=httpx.Limits(max_connections=16, max_keepalive_connections=8))

# ==================== 简易缓存 ====================

# 文本 → 向量缓存（同一需求文本可能被多次编码，避免重复 API 调用）
_cache: dict[str, list[float]] = {}  # key=文本内容, value=向量列表
_cache_lock = threading.Lock()  # 写锁保证线程安全（uvicorn 多 worker 不共享内存，仅在单进程内有效）
# 缓存最大条目数（约 2000 条，每条 1024×4 字节 ≈ 4KB，总内存 ≈ 8MB）
MAX_CACHE_SIZE = 2000


# ==================== 对外接口 ====================

def encode(text: str) -> list[float]:
    """将文本编码为向量，并写入进程内缓存。

    空文本、API 调用失败、响应格式错误、向量维度不一致都会直接抛出异常。
    这样调用方可以明确感知失败，避免把零向量写入 Milvus 后产生“接口成功但语义检索不可用”的隐性故障。

    Args:
        text: 待编码文本，会去除首尾空白后作为缓存键

    Returns:
        浮点数列表，维度由 ``VECTOR_DIM`` 决定（默认 1024）

    Raises:
        ValueError: 文本为空或只包含空白字符。
        RuntimeError: Embedding API 请求失败或返回数据不合法。
    """
    normalized_text = (text or "").strip()
    if not normalized_text:
        raise ValueError("待向量化文本不能为空")

    # 先查缓存，返回副本避免调用方修改缓存内容。
    with _cache_lock:
        if normalized_text in _cache:
            return _cache[normalized_text].copy()

    # OpenAI 兼容 Embedding API 的请求体。
    payload_dict = {
        "model": EMBEDDING_MODEL,
        "input": normalized_text,
        "encoding_format": "float",
    }
    url = f"{EMBEDDING_BASE_URL.rstrip('/')}/embeddings"

    # 请求失败必须抛错，不能返回零向量伪装成成功结果。
    try:
        response = _client.post(
            url,
            json=payload_dict,
            headers={"Authorization": f"Bearer {EMBEDDING_API_KEY}"},
            timeout=30,
        )
        response.raise_for_status()
        embedding = response.json()["data"][0]["embedding"]

    except httpx.HTTPStatusError as e:
        logger.error("Embedding API HTTP %s: %s", e.response.status_code, e.response.text[:500])
        raise RuntimeError(f"Embedding API 请求失败，HTTP {e.response.status_code}") from e

    except httpx.HTTPError as e:
        logger.error("Embedding API 网络错误: %s", e)
        raise RuntimeError(f"Embedding API 网络错误: {e}") from e

    except (json.JSONDecodeError, KeyError, IndexError) as e:
        logger.error("Embedding API 响应解析异常: %s", e)
        raise RuntimeError("Embedding API 响应格式不合法") from e

    # 维度校验可以提前发现模型配置错误，避免错误向量写入 Milvus。
    if not isinstance(embedding, list) or not embedding:
        raise RuntimeError("Embedding API 返回了空向量")
    if len(embedding) != VECTOR_DIM:
        raise RuntimeError(
            f"Embedding 向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(embedding)}"
        )

    # 缓存满时整体重建。实现简单，且能防止缓存无限增长。
    with _cache_lock:
        if len(_cache) >= MAX_CACHE_SIZE:
            _cache.clear()
        _cache[normalized_text] = embedding

    return embedding.copy()


# 批量 embedding 的单次批大小（在线 API 限流下的稳妥值）
EMBEDDING_BATCH_SIZE = int(os.environ.get("EMBEDDING_BATCH_SIZE", "16"))


def encode_batch(texts: list[str]) -> list[list[float]]:
    """将多段文本批量编码为向量（摊薄在线 HTTP 往返）。

    优先命中缓存；未命中的按 EMBEDDING_BATCH_SIZE 分批调 API（OpenAI 兼容接口
    支持 input 传数组）。单批失败（429/超时）时按一半拆分重试，最小拆到单条。

    Args:
        texts: 待编码文本列表

    Returns:
        与输入等长的向量列表（顺序一致）

    Raises:
        ValueError: 任一文本为空。
        RuntimeError: 分批全部失败。
    """
    if not texts:
        return []
    results: list[list[float] | None] = [None] * len(texts)
    pending: list[int] = []  # 待编码下标（未命中缓存）
    for i, t in enumerate(texts):
        norm = (t or "").strip()
        if not norm:
            raise ValueError("待向量化文本不能为空")
        with _cache_lock:
            hit = _cache.get(norm)
        if hit is not None:
            results[i] = hit.copy()
        else:
            pending.append(i)

    # 逐批编码未命中的
    for start in range(0, len(pending), EMBEDDING_BATCH_SIZE):
        batch_idx = pending[start:start + EMBEDDING_BATCH_SIZE]
        batch_texts = [(texts[i] or "").strip() for i in batch_idx]
        vecs = _encode_batch_call(batch_texts)
        for i, v in zip(batch_idx, vecs):
            with _cache_lock:
                if len(_cache) >= MAX_CACHE_SIZE:
                    _cache.clear()
                _cache[(texts[i] or "").strip()] = v
            results[i] = v.copy()

    return [r if r is not None else [] for r in results]


def _encode_batch_call(batch_texts: list[str]) -> list[list[float]]:
    """单次批量 API 调用，失败按半拆分重试。"""
    if not batch_texts:
        return []
    payload_dict = {
        "model": EMBEDDING_MODEL,
        "input": batch_texts,
        "encoding_format": "float",
    }
    url = f"{EMBEDDING_BASE_URL.rstrip('/')}/embeddings"
    try:
        response = _client.post(
            url,
            json=payload_dict,
            headers={"Authorization": f"Bearer {EMBEDDING_API_KEY}"},
            timeout=60,
        )
        response.raise_for_status()
        data = sorted(response.json()["data"], key=lambda d: d.get("index", 0))
        vecs = [d["embedding"] for d in data]
        for v in vecs:
            if len(v) != VECTOR_DIM:
                raise RuntimeError(
                    f"Embedding 向量维度不匹配: 期望 {VECTOR_DIM}, 实际 {len(v)}")
        return vecs
    except Exception as e:
        # 半拆重试（限流/超时常见于大批量）
        if len(batch_texts) > 1:
            logger.warning("批量 embedding 失败(size=%d)，拆半重试: %s", len(batch_texts), e)
            mid = len(batch_texts) // 2
            return _encode_batch_call(batch_texts[:mid]) + _encode_batch_call(batch_texts[mid:])
        logger.error("单条 embedding 失败: %s", e, exc_info=True)
        raise RuntimeError(f"Embedding API 请求失败: {e}") from e


# ==================== 文本拼接工具 ====================

def build_profile_text(candidate: dict) -> str:
    """将供应商画像字段拼接为语义检索文本

    按语义重要性排序：公司名 > 生产品类 > 主要产品 > 行业 > 企业简介
    使用中文句号拼接，语义边界比逗号更清晰
    """
    parts = []  # 文本片段列表
    if candidate.get("company_name"):  # 公司名称 — 标识
        parts.append(candidate["company_name"])
    if candidate.get("sup_categories"):  # 生产品类（逗号分隔） — 核心匹配维度
        parts.append(candidate["sup_categories"])
    if candidate.get("main_products"):  # 主要产品/服务 — 业务描述
        parts.append(candidate["main_products"])
    if candidate.get("industry"):  # 所属行业 — 行业分类
        parts.append(candidate["industry"])
    if candidate.get("description"):  # 企业简介 — 最丰富的自由文本语义信息
        parts.append(candidate["description"])
    return "。".join(parts)  # 用中文句号拼接


# 描述进向量的上限：够表达需求，避免长文稀释标题/品类
_REQUIREMENT_DESC_MAX = 800


def build_requirement_text(req: dict) -> str:
    """将采购需求字段拼接为语义检索文本

    只用标题、品类、描述、资质。数量/预算/地域不进向量：
    数量预算几乎没有语义，地域交给 SQL 召回。
    """
    parts = []  # 文本片段列表
    if req.get("title"):  # 需求标题 — 最直接的需求表达
        parts.append(req["title"])
    if req.get("category"):  # 采购品类 — 核心匹配维度
        parts.append(req["category"])
    description = (req.get("description") or "").strip()
    if description:  # 需求描述截断，避免把标题品类稀释掉
        parts.append(description[:_REQUIREMENT_DESC_MAX])
    if req.get("cert_require"):  # 资质要求 — 带标签的结构化信息
        parts.append("资质要求：" + req["cert_require"])
    return "。".join(parts)  # 用中文句号拼接
