"""
Token 计量工具

按字符类别加权估算 token 数，替代 len() 字符计数。
中文合同/规格书里中英数混排，纯 len() 会让块大小漂移：
  - 汉字 1 字 ≈ 1 token
  - 英文/数字词（如 HT-2024-001）整段 ≈ 1.3 token/词，按 len() 会严重高估
  - 标点/空白按 0.5 折

不引入 tiktoken / transformers：本环境无 HF 缓存、无 GPU，
重型 tokenizer 反而会在内网静默回退。启发式 ±15% 误差对切分上限判断足够。
"""
import re

# 汉字/全角标点/日韩字符：视为 1 token/字
_CJK_RE = re.compile(r"[一-鿿　-〿＀-￯぀-ヿ]")
# 英文数字词组（含连字符/下划线/斜杠，保住 HT-2024-001 这类整词）
_WORD_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9\-_/.]*")

# 权重与安全系数
_CJK_WEIGHT = 1.0
_WORD_WEIGHT = 1.3
_OTHER_WEIGHT = 0.5
_SAFETY = 1.1


def count_tokens(text: str) -> int:
    """估算文本 token 数（向上取整）。

    Args:
        text: 待计量文本

    Returns:
        估算 token 数；空文本返回 0
    """
    if not text:
        return 0
    cjk = len(_CJK_RE.findall(text))
    words = len(_WORD_RE.findall(text))
    other = max(len(text) - cjk - sum(len(w) for w in _WORD_RE.findall(text)), 0)
    raw = cjk * _CJK_WEIGHT + words * _WORD_WEIGHT + other * _OTHER_WEIGHT
    return int(raw * _SAFETY) + 1
