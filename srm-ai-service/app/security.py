"""
SRM AI Service 鉴权模块
所有非 /api/health 端点校验 X-API-Key 头，防止 Python 服务在公网裸奔被未授权调用。
鉴权 token 从环境变量 AI_SERVICE_TOKEN 读取，与 DeepSeek/Embedding 密钥解耦。
"""
import os  # 读取环境变量
import logging  # 日志模块
import secrets  # 恒定时间比较，防时序侧信道
from fastapi import Request, HTTPException  # 请求对象与 HTTP 异常
from starlette.responses import JSONResponse  # 统一 401 响应体

logger = logging.getLogger("srm-ai.security")  # 模块日志器

# 鉴权 token：无环境变量时回退空串（拒绝所有请求，fail-closed，避免裸奔）
AI_SERVICE_TOKEN = os.environ.get("AI_SERVICE_TOKEN", "")

# 无需鉴权的路径前缀（健康检查探针 / CI 部署探活）
PUBLIC_PATHS = ("/api/health",)


async def api_key_middleware(request: Request, call_next):
    """X-API-Key 鉴权中间件。

    未匹配 token 一律返回 401（不区分无 key 与错 key，不泄露鉴权细节）；
    豁免 /api/health 供负载均衡/CI 探活。
    """
    path = request.url.path
    if path.startswith(PUBLIC_PATHS):
        return await call_next(request)  # 健康检查放行

    # fail-closed：token 未配置或请求头缺失/不匹配均拒绝。
    # 注意默认值必须为""，不能填真实 token，否则"不带 header"会被默认值兜底放行，鉴权形同虚设
    supplied = request.headers.get("X-API-Key", "")
    # 恒定时间比较（compare_digest）避免按字节短路比较泄露 token 前缀（时序侧信道）。
    # 空 token 单独提前拒绝：compare_digest("", "") 会判定相等，若 AI_SERVICE_TOKEN 未配置
    # 且请求头为空，必须拒绝而非放行——所以先判 not AI_SERVICE_TOKEN。
    if not AI_SERVICE_TOKEN or not secrets.compare_digest(
            supplied.encode("utf-8"), AI_SERVICE_TOKEN.encode("utf-8")):
        logger.warning("拒绝未授权请求: method=%s path=%s", request.method, path)
        return JSONResponse(status_code=401, content={"detail": "Unauthorized"})

    return await call_next(request)  # 鉴权通过，继续处理
