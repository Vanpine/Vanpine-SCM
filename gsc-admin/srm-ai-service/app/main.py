"""
SRM AI Service - FastAPI Application Entry Point
Start: uvicorn app.main:app --host 127.0.0.1 --port 8100
"""
import logging  # Python 标准日志模块
import sys  # 标准输出流
from dotenv import load_dotenv  # 加载 .env 环境变量（须在导入业务模块前执行）
load_dotenv()  # 读取 .env 里的 AI_SERVICE_TOKEN 等，否则 security 模块读到空 token
from fastapi import FastAPI  # FastAPI 框架
from app.api.routes import router  # API 路由定义
from app.security import api_key_middleware  # X-API-Key 鉴权中间件

# ==================== 日志配置 ====================

# ANSI 颜色码（PyCharm / Windows Terminal 均支持）
COLORS = {
    logging.DEBUG:    "\033[90m",   # 灰色
    logging.INFO:     "\033[36m",   # 青色（显眼）
    logging.WARNING:  "\033[33m",   # 黄色
    logging.ERROR:    "\033[31m",   # 红色
    logging.CRITICAL: "\033[1;31m", # 加粗红色
}
RESET = "\033[0m"


class ColoredFormatter(logging.Formatter):
    """按日志级别着色的 Formatter"""
    def format(self, record):
        """格式化单条日志，并为级别和消息追加 ANSI 颜色码。"""
        # 未命中颜色映射时使用空串，保持原始文本
        color = COLORS.get(record.levelno, "")
        # 只修改当前 record 的展示字段，不污染全局 Formatter 状态
        record.levelname = f"{color}{record.levelname}{RESET}"
        record.msg = f"{color}{record.msg}{RESET}"
        return super().format(record)


# 手动配置 root logger（basicConfig 不支持自定义 Formatter）
handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(ColoredFormatter(
    fmt="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
))
logging.root.setLevel(logging.DEBUG)
logging.root.handlers = [handler]
# 第三方库日志级别调高，避免刷屏
logging.getLogger("pymilvus").setLevel(logging.WARNING)  # Milvus 内部日志只显示 WARNING+
logging.getLogger("httpx").setLevel(logging.WARNING)  # HTTP 客户端日志
logging.getLogger("urllib3").setLevel(logging.WARNING)  # urllib 日志

logger = logging.getLogger("srm-ai")  # 项目专用日志器
logger.info("SRM AI Service starting...")  # 启动日志

# ==================== FastAPI 应用 ====================

app = FastAPI(
    title="SRM AI Service",  # Swagger 文档标题
    version="1.0.0",  # 语义版本号
    description="Global SCM AI Platform - Smart Matching, Risk Prediction, Inventory Alert",
)

# 注册 X-API-Key 鉴权中间件（/api/health 豁免，其余端点须带 token）
app.middleware("http")(api_key_middleware)

# 注册 API 路由
app.include_router(router)

# ==================== 直接启动入口 ====================
if __name__ == "__main__":
    import uvicorn  # ASGI 服务器
    uvicorn.run(app, host="127.0.0.1", port=8100)  # 绑定 127.0.0.1:8100
