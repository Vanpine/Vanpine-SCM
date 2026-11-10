"""扫描页识别（VLM）。

扫描件没有文本层，先渲染成图片，再交给多模态模型转成 Markdown：
  - 复用现有 DeepSeek 多模态能力（deepseek-flash 已实测支持 base64 图片）；
  - 输出 Markdown 而不是纯文本，保留标题/列表/表格结构，供现有解析链路直接使用；
  - 单页失败不影响其他页，由调用方统计失败页并交给质量门禁决定是否整篇拒绝。

配置（均可缺省，缺省复用 DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL）：
  VLM_ENABLED        开关，默认 true
  VLM_MODEL          模型名，默认 deepseek-flash
  VLM_BASE_URL       OpenAI 兼容地址，默认 https://api.deepseek.com/
  VLM_API_KEY        API 密钥，默认复用 DEEPSEEK_API_KEY
  VLM_DPI            渲染分辨率，默认 200
  VLM_TIMEOUT        单页超时秒数，默认 120
  VLM_MAX_TOKENS     单页输出上限，默认 4096
  VLM_CONCURRENCY    并发页数，默认 3（过高容易被限流）
  VLM_MAX_PAGES      单文档最多 OCR 页数，默认 20（Java 侧同步读超时 120s）
  VLM_MIN_CHARS      OCR 结果低于此字符数视为失败，默认 20
"""
import base64
import io
import logging
import os
import re
import threading
import unicodedata
from concurrent.futures import ThreadPoolExecutor, as_completed

from app.services.reasoning import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL

logger = logging.getLogger("srm-ai.vlm")

# ==================== 配置 ====================

VLM_ENABLED = os.environ.get("VLM_ENABLED", "true").lower() in ("1", "true", "yes")
VLM_MODEL = os.environ.get("VLM_MODEL", "deepseek-flash")
VLM_BASE_URL = os.environ.get("VLM_BASE_URL") or DEEPSEEK_BASE_URL
VLM_API_KEY = os.environ.get("VLM_API_KEY") or DEEPSEEK_API_KEY
VLM_DPI = int(os.environ.get("VLM_DPI", "200"))
VLM_TIMEOUT = int(os.environ.get("VLM_TIMEOUT", "120"))
VLM_MAX_TOKENS = int(os.environ.get("VLM_MAX_TOKENS", "4096"))
VLM_CONCURRENCY = max(1, int(os.environ.get("VLM_CONCURRENCY", "3")))
VLM_MAX_PAGES = int(os.environ.get("VLM_MAX_PAGES", "20"))
VLM_MIN_CHARS = int(os.environ.get("VLM_MIN_CHARS", "20"))
# OCR 质量分阈值：低于 FAIL 视为识别失败；低于 WARN 入库但提示人工核对
VLM_QUALITY_FAIL = float(os.environ.get("VLM_QUALITY_FAIL", "0.35"))
VLM_QUALITY_WARN = float(os.environ.get("VLM_QUALITY_WARN", "0.65"))
# 质量分里「长度是否合理」的参考字符数（非空白）
_QUALITY_REF_CHARS = 200

# 乱码/不可见字符
_INVALID_CHARS = set("�□")
# 合法符号白名单（不在 Unicode L/N/P/M 分类里但正文会出现）
_EXTRA_VALID_CHARS = set("|#*℃±×÷≤≥≠°¥€$")
_WHITESPACE_RE = re.compile(r"\s")

# OCR 提示词：Markdown 输出 + 禁止编造，供现有解析器直接消费
_OCR_PROMPT = """你是文档数字化助手。把图片中的内容完整、准确地转成 Markdown。

要求：
1. 保留标题层级：主标题用 #，一级章节用 ##，二级章节用 ###
2. 保留列表与编号
3. 表格转为 Markdown 管道表，必须保留表头
4. 公式转为 LaTeX
5. 不要输出页眉、页脚、页码、水印
6. 不要添加原文没有的内容；看不清的字用 □ 代替，不要猜测
7. 只输出 Markdown 正文，不要任何解释或前后缀"""

# 视觉客户端单例（与对话客户端分开：OCR 需要 temperature=0、非流式、更大输出上限）
_vision_llm = None
_lock = threading.Lock()


def is_available() -> bool:
    """VLM 是否可用（开关打开且配置了密钥）。"""
    return VLM_ENABLED and bool(VLM_API_KEY)


def _get_vision_llm():
    """延迟创建视觉客户端（单例，复用连接池）。"""
    global _vision_llm
    if _vision_llm is None:
        with _lock:
            if _vision_llm is None:
                from langchain_openai import ChatOpenAI

                _vision_llm = ChatOpenAI(
                    model=VLM_MODEL,
                    api_key=VLM_API_KEY,
                    base_url=VLM_BASE_URL,
                    temperature=0,          # OCR 要确定性
                    max_tokens=VLM_MAX_TOKENS,
                    streaming=False,
                    request_timeout=VLM_TIMEOUT,
                )
                logger.info("VLM 客户端就绪: model=%s, base_url=%s", VLM_MODEL, VLM_BASE_URL)
    return _vision_llm


def ocr_pages(pdf_bytes: bytes, page_indexes: list[int]) -> dict[int, tuple[str, float]]:
    """对指定页做 OCR。

    Args:
        pdf_bytes: PDF 原始字节
        page_indexes: 需要 OCR 的页下标（0-based）

    Returns:
        {页下标: (Markdown 文本, 质量分)}；识别失败的页为 ("", 0.0)
    """
    if not page_indexes or not is_available():
        return {}

    images = render_page_images(pdf_bytes, page_indexes)
    if not images:
        return {}

    results: dict[int, tuple[str, float]] = {}
    workers = min(VLM_CONCURRENCY, len(images))
    logger.info("开始 OCR: %d 页, 并发=%d, dpi=%d", len(images), workers, VLM_DPI)

    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="vlm") as pool:
        futures = {pool.submit(_ocr_one, png, idx): idx for idx, png in images.items()}
        for future in as_completed(futures):
            idx = futures[future]
            try:
                results[idx] = future.result()
            except Exception as e:
                logger.warning("OCR 第 %d 页异常: %s", idx + 1, e)
                results[idx] = ("", 0.0)

    succeeded = sum(1 for text, _ in results.values() if text)
    logger.info("OCR 完成: 成功 %d/%d 页", succeeded, len(results))
    return results


def ocr_quality(text: str) -> float:
    """估算 OCR 结果质量（0~1）。

    VLM 不返回逐字置信度，这里用可观测的代理指标：
      - 乱码率：□ / � / 不可见控制字符；
      - 有效字符率：正文/标点/表格符号占比，符号乱码（◆▲）会拉低；
      - 长度合理性：一页只识别出几个字说明大概率失败。
    """
    stripped = (text or "").strip()
    if not stripped:
        return 0.0

    total = 0
    valid = 0
    invalid = 0
    for ch in stripped:
        if ch.isspace():
            continue
        total += 1
        if ch in _INVALID_CHARS:
            invalid += 1
            continue
        category = unicodedata.category(ch)
        if category[0] in ("L", "N", "P", "M") or ch in _EXTRA_VALID_CHARS:
            valid += 1
        else:
            # 其它符号（◆、▲、私用区字符等）视为噪声
            invalid += 1

    if total == 0:
        return 0.0

    invalid_score = 1.0 - invalid / total
    valid_score = valid / total
    length_score = min(1.0, total / _QUALITY_REF_CHARS)
    return round(0.35 * invalid_score + 0.45 * valid_score + 0.20 * length_score, 2)


def render_page_images(pdf_bytes: bytes, page_indexes: list[int],
                       dpi: int = VLM_DPI) -> dict[int, bytes]:
    """把指定页渲染成 PNG 字节（pdfplumber + pypdfium2，无需额外系统依赖）。"""
    import pdfplumber

    images: dict[int, bytes] = {}
    try:
        with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
            total = len(pdf.pages)
            for idx in page_indexes:
                if idx < 0 or idx >= total:
                    continue
                image = pdf.pages[idx].to_image(resolution=dpi)
                buf = io.BytesIO()
                image.original.convert("RGB").save(buf, format="PNG")
                images[idx] = buf.getvalue()
    except Exception as e:
        logger.error("页面渲染失败: %s", e, exc_info=True)
    return images


def _ocr_one(png_bytes: bytes, page_index: int) -> tuple[str, float]:
    """单页 OCR：base64 图片 → 多模态模型 → Markdown；失败重试一次。

    Returns:
        (Markdown 文本, 质量分)；失败返回 ("", 0.0)
    """
    data_url = "data:image/png;base64," + base64.b64encode(png_bytes).decode("ascii")
    message = ("user", [
        {"type": "text", "text": _OCR_PROMPT},
        {"type": "image_url", "image_url": {"url": data_url}},
    ])

    for attempt in (1, 2):
        try:
            response = _get_vision_llm().invoke([message])
            text = _clean_output(_content_text(getattr(response, "content", "")))
            if len(text) < VLM_MIN_CHARS:
                logger.warning("OCR 第 %d 页输出过短(%d 字符)，尝试 %d/2",
                               page_index + 1, len(text), attempt)
                continue

            score = ocr_quality(text)
            if score < VLM_QUALITY_FAIL:
                logger.warning("OCR 第 %d 页质量过低(%.2f < %.2f)，尝试 %d/2",
                               page_index + 1, score, VLM_QUALITY_FAIL, attempt)
                continue

            logger.info("OCR 第 %d 页成功: %d 字符, 质量=%.2f",
                        page_index + 1, len(text), score)
            return text, score
        except Exception as e:
            logger.warning("OCR 第 %d 页失败(尝试 %d/2): %s", page_index + 1, attempt, e)
    return "", 0.0


def _content_text(content) -> str:
    """把 LangChain 返回的 content（str 或 content block 列表）统一成文本。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(block.get("text") or "")
        return "\n".join(parts)
    return str(content or "")


def _clean_output(text: str) -> str:
    """去掉模型偶发包裹的 Markdown 代码围栏。"""
    text = (text or "").strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    return text
