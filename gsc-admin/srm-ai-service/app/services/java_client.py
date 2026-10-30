"""
Java 后端内部接口客户端

Python 检索命中子块后，需按 parent_key 回查父块全文——父块存在 Java 侧 MySQL
（ai_doc_parent_chunks），Python 不直连数据库，通过 gsc-service-ai 的内部接口取。

鉴权复用 AI 工具令牌（与 tools.py 调 /internal/ai/tool 同一套）：
带当轮 tool_token 作 Authorization: Bearer，Java 验签拿 tenantId。
"""
import logging  # 日志模块
import os  # 读取环境变量

import httpx  # HTTP 客户端（连接池复用）

logger = logging.getLogger("srm-ai.java_client")

# Java AI 服务内部接口基址（直连 gsc-service-ai 端口，不走网关）
_PARENTS_API_URL = os.environ.get(
    "AI_PARENTS_API_URL", "http://127.0.0.1:3085/internal/ai/parents"
)
# 父块回查超时（秒）：短，失败即回退子块原文，不拖垮问答
_TIMEOUT = float(os.environ.get("AI_PARENTS_TIMEOUT", "5"))

# 共享 HTTP 客户端：与 tools.py 打的是同一个 Java 服务，复用连接避免每次回查重新握手
_client = httpx.Client(limits=httpx.Limits(max_connections=16, max_keepalive_connections=8))


def fetch_parents(parent_keys: list[str], tool_token: str) -> dict[str, dict]:
    """按 parent_key 批量取父块（Java 内部接口）。

    Args:
        parent_keys: 父块 key 列表
        tool_token:  当轮 AI 工具令牌（Bearer 鉴权；空则直接返回空，调用方回退子块原文）

    Returns:
        {parent_key: {"content", "heading_path", "token_count"}}
        失败（无令牌/网络/格式错/非 200）返回空 dict，调用方回退子块原文。
    """
    if not parent_keys or not tool_token:
        return {}

    try:
        response = _client.post(
            _PARENTS_API_URL,
            json={"parent_keys": list(parent_keys)},
            headers={"Authorization": f"Bearer {tool_token}"},
            timeout=_TIMEOUT,
        )
        response.raise_for_status()
        body = response.json()
    except Exception as e:
        # 取不到父块只影响上下文丰富度，不影响问答（回退子块原文）
        logger.warning("父块回查失败，回退子块原文: %s", e)
        return {}

    # Java 统一响应 {code, message, data}；data 为父块列表
    data = body.get("data") if isinstance(body, dict) else None
    if not isinstance(data, list):
        return {}
    out: dict[str, dict] = {}
    for item in data:
        if not isinstance(item, dict):
            continue
        key = item.get("parentKey")
        if key:
            out[str(key)] = {
                "content": item.get("content", ""),
                "heading_path": item.get("headingPath") or [],
                "token_count": item.get("tokenCount", 0),
            }
    return out
