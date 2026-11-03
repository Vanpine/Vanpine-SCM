"""
天气问句解析 — 从「北京天气」「广州未来7天天气」抽出 city/days。

独立成模块：chat.py 的强制短路分支与 prompts 的锚点注入都要用它，
但它自身不依赖对话链路的任何东西（纯字符串处理），可单独测试。
"""
import re



# 天气问句关键词：检索跳过 + 注入 LIVE_WEATHER_RULE（与 DOMAIN_HINTS 对齐）
WEATHER_QUERY_KEYWORDS = ("天气", "weather", "台风", "暴雨", "气象")


def is_weather_question(question: str) -> bool:
    """是否在问实时天气（用于跳过知识库、强制走 get_weather）。"""
    text = (question or "").lower()
    return any(keyword in text for keyword in WEATHER_QUERY_KEYWORDS)


# 问句噪声：抽地点前去掉，避免「帮我查一下明天北京天气」把整句当城市名
_WEATHER_NOISE_RE = re.compile(
    r"(帮我|请|查一下|查询一下|查询|看看|看下|的|未来|近|今天|明天|后天|实时|当地|状况|如何|怎么样|怎样)"
)
_WEATHER_PUNCT_RE = re.compile(r"[?？。！!，,、.\s]+")
_WEATHER_DAYS_RE = re.compile(r"(\d+)\s*天")
_WEATHER_TAIL_RE = re.compile(r"(天气预报|天气状况|天气|预报|台风|暴雨|气象).*$")
# 抱怨/反问不是地名（「我tm叫你啥天气」）
_WEATHER_BAD_CITY_RE = re.compile(r"啥|什么|哪|怎么|查不了|无法|没有|你|我")
# 指代/方位残留词：抽完噪声后仍是「广州那边」「刚提到的交付单现在」，说明指的不是地点本身
_WEATHER_DEIXIS_RE = re.compile(r"(那边|这边|那里|这里|上面|下面|刚才|刚提|之前|前边|后边|现在|目前)")

# 业务对象词：命中说明问的是「某个单据所在地」的天气，不是「某地」的天气。
# 这类问句交给模型先查单据、再拿地点调 get_weather（强抽地点一定抽错）。
_WEATHER_BIZ_RE = re.compile(
    r"(合同|询盘|报价|交付单|交付|运单|订单|供应商|需求|采购|物流|节点|在途|发货|到货|货物|这批|这票)"
)


def parse_weather_query(question: str) -> dict | None:
    """从「北京天气」「广州未来7天天气」抽出 city/days；抽不出合法地点则返回 None。

    只做粗筛，不做城市词表校验（词表在 Java 侧 city-gazetteer.txt，重复维护会漂移）：
    - 指代/方位残留（「广州那边」「刚提到…现在」）→ 不是地名，交给正常工具循环
    - 业务对象词（「交付单/合同/供应商」）→ 问的是单据所在地，交给模型先查单据再查天气

    返回 None 时 **不表示不问天气**：is_weather_question 仍为真，LIVE_WEATHER_RULE 照注入，
    只是不强制短路主对话——模型可自行调 get_weather 或先查业务再查天气。
    """
    raw = (question or "").strip()
    if not raw or not is_weather_question(raw):
        return None
    # 问的是「某个业务对象所在地」的天气：不强行抽地点，让模型先定位对象
    if _WEATHER_BIZ_RE.search(raw):
        return None
    days = 3
    matched_days = _WEATHER_DAYS_RE.search(raw)
    if matched_days:
        days = min(max(int(matched_days.group(1)), 1), 7)
    city = _WEATHER_PUNCT_RE.sub("", raw)
    city = _WEATHER_DAYS_RE.sub("", city)
    city = _WEATHER_NOISE_RE.sub("", city)
    city = _WEATHER_TAIL_RE.sub("", city).strip()
    if not city or len(city) > 30:
        return None
    if _WEATHER_BAD_CITY_RE.search(city) or _WEATHER_DEIXIS_RE.search(city):
        return None
    return {"city": city, "days": days}
