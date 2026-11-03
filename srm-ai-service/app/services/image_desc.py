"""
图片内容描述生成

为什么需要：图片 token 成本是同体积文字的 10 倍（一张 ≈ 800~1600 token），
历史轮次一律不带原图。但用户追问「刚才那张图里的合同号」时模型会失明——
描述把图片内容压成一段文字（≈100 token），让历史轮次仍可指代。

与「压缩」的区别：压缩处理文字对话，本模块处理图片，两者都是把高成本输入
降级成低成本表示，再由 Java 侧按需注入上下文。
"""
import logging

from app.services.compaction import _cut_at_boundary  # 按句边界收口（与摘要共用同一截断口径）
from app.services.context_budget import _chunk_text  # 多模态块收成纯文本
from app.services.llm import _get_chat_llm  # 与对话共用同一套 LLM 配置

logger = logging.getLogger("srm-ai.image-desc")

# 描述长度上限（字）。历史里每张图占约 100 token，超了要截，否则多图累积会挤占窗口预算。
MAX_DESC_CHARS = 200

_DESC_PROMPT = """用一句话描述这张图片，供后续对话检索定位。

必须包含（有则写，无则跳过）：
- 图片类型：合同 / 发票 / 资质证书 / 单据 / 截图 / 产品照 / 表格 等
- 业务关键字段：单据编号、公司名、金额、日期、数量、状态
- 若含表格，写明表格主题与行数（不要逐行罗列）

禁止：
- 不要评价、不要猜测用途、不要写"这张图片展示了"
- 不要编造看不清的内容；看不清的部分直接不写
- 不要换行、不要用 markdown

直接输出一句话。"""


async def describe_image(image_url: str) -> str:
    """调多模态模型生成图片描述。

    Args:
        image_url: 图片可访问 URL（Java 侧现签的短效地址）

    Returns:
        单行描述文本；生成失败抛异常，由调用方决定是否记空串（不再重试）
    """
    llm = _get_chat_llm()
    # 与对话用同一套多模态 content 块格式（OpenAI 兼容）
    message = ("user", [
        {"type": "text", "text": _DESC_PROMPT},
        {"type": "image_url", "image_url": {"url": image_url}},
    ])
    response = await llm.ainvoke([message])
    desc = " ".join(_chunk_text(getattr(response, "content", None)).split())
    # 超长时按句边界收口：硬切会切出「合同号 HT2026」这种半截内容，模型会把残缺当事实
    desc = _cut_at_boundary(desc, MAX_DESC_CHARS)
    logger.info("图片描述生成完成: len=%d", len(desc))
    return desc
