"""
在途天气异常 → DeepSeek 处置建议。
规则引擎已经判定异常，这里只写可执行动作，不重新判断有没有风险。
"""
import logging
from langchain_core.prompts import ChatPromptTemplate
from app.services.reasoning import _get_llm, split_reason_lines

logger = logging.getLogger("srm-ai.weather_advice")

WEATHER_ADVICE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是跨境物流调度顾问。货物已在途或即将发运，规则引擎已判定天气异常。
根据给定事实写 4 到 6 条可执行处置建议。
要求：
1. 每条 30～70 字，必须点名地点或天气现象，禁止空话
2. 只依据用户给出的字段，不要编造未提供的航班、港口、关税、汇率
3. 尽量覆盖：是否暂缓提货/发运、是否改期、是否绕行或改运输方式、联系承运商确认作业、包装防护、通知买卖双方
4. 每条一行，不要编号、不要标题、不要前言"""),
    ("user", """天气异常：
- 地点：{place}
- 等级：{level}
- 摘要：{summary}
- 证据：{evidence}

交付信息：
- 交付单：{delivery_no}
- 承运商：{ship_company}
- 交货地：{delivery_place}
- 预计到货：{eta}
- 考核交期：{promised_date}
- 影响数量：{impact_qty}

处置建议："""),
])


def generate_weather_advice(payload: dict) -> list[str]:
    """成功返回模型建议；失败返回规则模板，调用方永不空列表。"""
    try:
        prompt = WEATHER_ADVICE_PROMPT.format(
            place=_field(payload.get("place")),
            level=_field(payload.get("level")),
            summary=_field(payload.get("summary")),
            evidence=_field(payload.get("evidence")),
            delivery_no=_field(payload.get("delivery_no")),
            ship_company=_field(payload.get("ship_company")),
            delivery_place=_field(payload.get("delivery_place")),
            eta=_field(payload.get("eta")),
            promised_date=_field(payload.get("promised_date")),
            impact_qty=_field(payload.get("impact_qty")),
        )
        text = _get_llm().invoke(prompt).content
        lines = split_reason_lines(text)
        if lines:
            return lines[:6]
        logger.warning("天气建议模型输出无法拆行，回退模板")
    except Exception as e:
        logger.warning("天气建议生成失败，回退模板: %s", e)
    return fallback_weather_advice(payload)


def fallback_weather_advice(payload: dict) -> list[str]:
    place = _field(payload.get("place")) or "当前节点"
    evidence = _field(payload.get("evidence")) or _field(payload.get("summary")) or "天气异常"
    return [
        f"关注{place}未来 3 日天气：{evidence}，作业前再确认一次能否进出港或上路。",
        "联系承运商确认该节点是否停航、封路或限行，不要按原计划盲发。",
        "对照考核交期评估改期或绕行；交期紧可考虑拆出紧急量改空运。",
        "通知买卖双方天气风险已触发，同步包装加固与到货预期，避免到厂无准备。",
    ]


def _field(value) -> str:
    if value is None:
        return ""
    return str(value).strip()
