"""AI 对话意图预路由模块。

在进入 RAG / Function Calling 之前先判断问题类型，
避免「我是谁」「你能做什么」这类元问题被业务工具和历史大表劫持，
也避免「查我的合同」这类业务数据问题无谓地去查知识库。

第一版用确定性规则分类（元问题高度固定，规则足够，零额外延迟），
后续若命中率不足再引入 LLM 分类兜底。
"""

import re  # 去标点（「在吗？」「你好。」要能识别成闲聊）
from dataclasses import dataclass

# 意图枚举：
# identity_self      -> 我是谁 / 当前用户（问「我自己」）
# identity_assistant -> 你是谁 / 你叫什么（问「助手」）
# capability     -> 你能做什么 / 支持哪些操作
# personal_fact  -> 个人陈述（偏好/过敏/健康等），只确认不落业务
# smalltalk      -> 你好 / 谢谢 / 再见
# image          -> 图片理解，走纯多模态
# business       -> 业务/知识问答，统一走 Agent 工具循环（知识库作为一种工具，由模型自选）
#
# 注：不再用关键词区分「业务数据」与「知识问答」——两者文本无法可靠区分，
# 由模型在工具循环中自行选择调用业务工具还是 search_knowledge_base。


@dataclass(frozen=True)
class IntentResult:
    intent: str


# 「我是谁」类：问的是当前登录用户自己，答案是「你的账号/角色」。
_IDENTITY_SELF_PATTERNS = (
    "我是谁", "我是哪个用户", "当前用户", "我的身份", "我的角色", "我的账号",
    "who am i", "current user",
)

# 「你是谁」类：问的是助手自己，答案必须是「我是什么助手、能干什么」。
# ⚠️ 必须与上面分开——两者答案是两套，合并成一个意图会让「你是谁」
# 被答成「你是当前登录用户…」（实际发生过的 bug）。
_IDENTITY_ASSISTANT_PATTERNS = (
    "你是谁", "你叫什么", "你的身份", "你是哪位", "你是什么助手", "你是哪个助手",
    "你是什么ai",
)

_CAPABILITY_PATTERNS = (
    "你能做什么", "你会什么", "你可以做什么", "支持哪些操作", "有哪些能力",
    "你能帮我做什么", "能帮我做什么", "你的能力", "功能列表",
    "what can you do",
)

_SMALLTALK_PATTERNS = (
    "你好", "您好", "嗨", "在吗", "谢谢", "感谢", "再见",
    "hello", "hi", "thanks",
)

# 个人陈述（高确信度词）。裸关键词包含误判率太高（「合同里有过敏责任条款吗」
# 会被「过敏」带偏），必须结合「第一人称 + 无业务词」才判 personal_fact。
_PERSONAL_FACT_PATTERNS = (
    "过敏", "我不能吃", "我不能接触", "我偏好", "我讨厌", "我需要你记住", "记住我",
)

# 第一人称：个人陈述一定是用户在说自己（「我对花生过敏」），
# 缺了它多半是在问业务（「合同里有过敏责任条款吗」）
_FIRST_PERSON_RE = re.compile(r"(我|俺|咱|本人|我自己)")

# 业务词：命中说明问的是业务对象而非个人情况，直接落回 business
# （「记住我【刚才查的合同】」「合同里的【过敏】责任条款」）
_BIZ_WORDS_RE = re.compile(
    r"(合同|条款|询盘|报价|供应商|交付单|交付|订单|需求|采购|物流|运单|发票|协议|资质|准入)"
)

# 闲聊去标点：把句末语气/标点剥掉再比对（「在吗？」「你好。」「谢谢！」）
_TRAILING_PUNCT_RE = re.compile(r"[\s?？!！。.~～,，、]+$")


def _is_smalltalk(text: str) -> bool:
    """整句即闲聊（去尾部标点后精确匹配），避免「在吗？」因多一个问号被判成业务。"""
    return _TRAILING_PUNCT_RE.sub("", text) in _SMALLTALK_PATTERNS


def _is_personal_fact(text: str) -> bool:
    """个人陈述须同时满足「含个人陈述词 + 第一人称 + 无业务词」。"""
    if not any(p in text for p in _PERSONAL_FACT_PATTERNS):
        return False
    if _BIZ_WORDS_RE.search(text):
        return False
    return _FIRST_PERSON_RE.search(text) is not None


def classify(question: str, has_images: bool = False) -> IntentResult:
    """优先用确定性规则分类，不额外调 LLM。

    只区分「元问题（零延迟直答）」与「业务/知识问题（走 Agent 工具循环）」。
    业务数据 vs 知识问答不再前置判断——交给模型在工具循环里选。
    """
    if has_images:
        return IntentResult(intent="image")

    text = (question or "").strip().lower()

    if any(p in text for p in _IDENTITY_SELF_PATTERNS):
        return IntentResult(intent="identity_self")

    if any(p in text for p in _IDENTITY_ASSISTANT_PATTERNS):
        return IntentResult(intent="identity_assistant")

    if any(p in text for p in _CAPABILITY_PATTERNS):
        return IntentResult(intent="capability")

    if _is_personal_fact(text):
        return IntentResult(intent="personal_fact")

    if _is_smalltalk(text):
        return IntentResult(intent="smalltalk")

    return IntentResult(intent="business")

