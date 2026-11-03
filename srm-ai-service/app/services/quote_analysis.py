"""
报价比价 AI 分析服务
提供三个核心能力：综合比价分析、授标理由生成、询盘内容辅助
复用 reasoning.py 的 DeepSeek LLM 客户端
"""
import json
import logging
import re
from langchain_core.prompts import ChatPromptTemplate
from app.services.reasoning import _get_llm

logger = logging.getLogger("srm-ai.quote_analysis")

# ==================== Prompt 模板 ====================

QUOTE_ANALYSIS_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是供应链采购专家，拥有20年全球采购经验。你的任务是对供应商报价进行综合分析。

分析原则：
1. 客观公正：基于数据说话，不做主观臆断
2. 全面考量：不只盯价格，要综合交期、付款条件、供应商资质
3. 风险意识：识别异常报价、不合理承诺
4. 可操作性：建议具体、可执行

输出要求：严格按指定 JSON 格式输出，不要任何额外文字，所有文本中文，分数保留1位小数"""),
    ("user", """## 询盘信息
- 编号：{inquiry_no}
- 标题：{inquiry_title}
- 品类：{inquiry_category}
- 数量：{inquiry_quantity} {inquiry_unit}
- 交货地点：{delivery_place}
- 付款条件：{payment_terms}
- 报价截止：{quote_deadline}

## 报价数据
{quotes_text}

## 分析要求

**重要：rankings 和 risks 中每个对象必须包含 quoteId 字段（使用输入数据中的数字），supplierName 必须使用报价数据中提供的真实供应商公司名，严禁使用"供应商1"等编号替代。**

按以下维度对每家供应商打分排名：

| 维度 | 权重 | 说明 |
|------|------|------|
| 总价 | 40% | 总价越低分越高 |
| 交期 | 20% | 交期越短分越高 |
| 付款条件 | 15% | 付款条件越宽松分越高 |
| 供应商画像完整度 | 15% | 完整度越高分越高 |
| 报价有效期 | 10% | 有效期越长分越高 |

风险检查：
- 报价异常低（低于平均30%以上）→ abnormal_low_price
- 交期异常短（低于平均50%以上）→ abnormal_short_delivery
- 报价即将过期（有效期<3天）→ expiring_soon

输出严格按以下 JSON 格式（不要包含```json```标记）：
{{
  "rankings": [
    {{
      "rank": 1,
      "quoteId": 123,
      "supplierName": "公司名",
      "score": 85.5,
      "strength": "一句话优势",
      "risk": "一句话风险（无则填'暂无显著风险'）",
      "comment": "一句话综合点评"
    }}
  ],
  "risks": [
    {{
      "type": "abnormal_low_price",
      "quoteId": 123,
      "supplierName": "公司名",
      "description": "具体风险描述"
    }}
  ],
  "recommendation": {{
    "quoteId": 123,
    "supplierName": "推荐的公司名",
    "reason": "推荐理由（引用具体数据对比）"
  }},
  "summary": "整体分析总结，2-3句话，概括本次比价的核心发现和建议"
}}"""),
])

AWARD_REASON_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是采购专家。根据选定供应商的报价和其他报价对比，生成一段专业的授标理由。

要求：
1. 篇幅控制在100字以内
2. 引用具体数据（价格、交期等）
3. 语气专业、客观
4. 不使用"我认为""我觉得"等主观用语
5. 直接陈述事实和理由，不要以"建议授标""推荐中标"等结尾
6. 直接输出理由文本，不要前缀、引号、编号"""),
    ("user", """## 中标供应商
- 公司：{winner_name}
- 总价：{winner_price} {winner_currency}
- 交期：{winner_delivery}天
- 付款条件：{winner_payment}

## 其他报价
{other_quotes_text}

## 询盘概要
- 品类：{inquiry_category}
- 数量：{inquiry_quantity} {inquiry_unit}

请生成授标理由："""),
])

INQUIRY_SUGGEST_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是供应链采购专家。根据采购品类和标题，为采购商提供填写建议。

输出严格按以下 JSON 格式（不要包含```json```标记）：
{{
  "specTemplate": "规格条款模板（列出关键参数维度，用分号分隔，≤200字）",
  "paymentSuggestions": ["建议1", "建议2", "建议3"],
  "deliverySuggestions": "合理的交货周期建议",
  "tips": "针对该类品类的采购注意事项（1-2句话）"
}}"""),
    ("user", """## 询盘信息
- 标题：{title}
- 品类：{category}
- 数量：{quantity} {unit}

请提供填写建议："""),
])


# ==================== 工具函数 ====================

def _call_llm(prompt_template: ChatPromptTemplate, max_tokens: int = 2048, **kwargs) -> str:
    """调用 DeepSeek LLM 并返回文本"""
    llm = _get_llm()
    llm.max_tokens = max_tokens
    prompt = prompt_template.format_messages(**kwargs)
    resp = llm.invoke(prompt)
    return resp.content.strip() if resp.content else ""


def _extract_json(text: str) -> dict:
    """从 LLM 返回文本中提取 JSON，容错处理 markdown 包裹"""
    text = text.strip()
    if text.startswith("```"):
        first_nl = text.find("\n")
        if first_nl > 0:
            text = text[first_nl + 1:]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        text = text[start:end + 1]
    return json.loads(text)


# ==================== 核心业务函数 ====================

def analyze_quotes(inquiry: dict, quotes: list[dict]) -> dict:
    """AI 综合比价分析

    Args:
        inquiry: {inquiryNo, title, category, quantity, unit, deliveryPlace,
                  paymentTerms, quoteDeadline}
        quotes: [{supplierCompanyName, totalPrice, unitPrice, currency,
                  deliveryDays, deliveryTerms, paymentTerms, validUntil,
                  remark, completenessScore}]

    Returns:
        {rankings, risks, recommendation, summary}
    """
    if not quotes:
        logger.info("比价分析: 无报价数据")
        return {
            "rankings": [],
            "risks": [],
            "recommendation": {"supplierName": "", "reason": "暂无报价数据，无法分析"},
            "summary": "当前没有供应商报价，请等待供应商提交报价后再进行分析。",
        }

    # 组装报价文本
    quotes_lines = []
    for i, q in enumerate(quotes):
        name = q.get("supplierCompanyName", f"供应商{i + 1}")
        price = q.get("totalPrice", "-")
        currency = q.get("currency", "CNY")
        unit_price = q.get("unitPrice", "-")
        delivery = q.get("deliveryDays", "-")
        d_terms = q.get("deliveryTerms", "-")
        valid_until = q.get("validUntil", "-")
        remark = q.get("remark", "")
        completeness = q.get("completenessScore", 0)

        line = (
            f"### 报价方：{name}（quoteId: {q.get('quoteId', 'N/A')}）\n"
            f"  - 总价：{price} {currency}（单价：{unit_price} {currency}）\n"
            f"  - 交期：{delivery}天\n"
            f"  - 交货条款：{d_terms}\n"
            f"  - 报价有效期至：{valid_until}\n"
            f"  - 报价说明：{remark if remark else '无'}\n"
            f"  - 供应商画像完整度：{completeness}分"
        )
        quotes_lines.append(line)

    quotes_text = "\n\n".join(quotes_lines)

    try:
        raw = _call_llm(
            QUOTE_ANALYSIS_PROMPT,
            max_tokens=2048,
            inquiry_no=inquiry.get("inquiryNo", ""),
            inquiry_title=inquiry.get("title", ""),
            inquiry_category=inquiry.get("category", ""),
            inquiry_quantity=str(inquiry.get("quantity", "")),
            inquiry_unit=inquiry.get("unit", ""),
            delivery_place=inquiry.get("deliveryPlace", "未指定"),
            payment_terms=inquiry.get("paymentTerms", "未指定"),
            quote_deadline=str(inquiry.get("quoteDeadline", "")),
            quotes_text=quotes_text,
        )
        result = _extract_json(raw)
        # Post-processing: 用 quoteId 映射真实 supplierName，防止 AI 编造名称
        name_map = {}
        for q in quotes:
            qid = q.get("quoteId")
            if qid is not None:
                name_map[int(qid)] = q.get("supplierCompanyName", "")

        def _resolve(item):
            """把 LLM 返回的匿名供应商名替换为真实供应商名。

            优先通过 quoteId 精确匹配；当 LLM 未返回 quoteId 时，
            降级解析 "供应商N" 序号，避免 AI 编造企业名称。
            """
            # 第一优先级：quoteId 是稳定主键，不受提示词中的序号影响
            qid = item.get("quoteId")
            if qid is not None:
                try:
                    key = int(qid)
                except (TypeError, ValueError):
                    key = None
                # 只有映射存在且真实名称非空时才覆盖，防止把有效结果改成空值
                if key is not None and key in name_map:
                    real = name_map[key]
                    if real:
                        item["supplierName"] = real
                        return
            # Fallback：匹配 "供应商N" 模式（LLM 未带 quoteId 时按序号取真实名）
            name = item.get("supplierName", "")
            m = re.match(r"^供应商(\d+)$", name)
            if m:
                # "供应商N" 是提示词中的序号，N 从 1 开始，列表索引从 0 开始
                idx = int(m.group(1)) - 1
                if 0 <= idx < len(quotes):
                    real_name = quotes[idx].get("supplierCompanyName")
                    if real_name:
                        item["supplierName"] = real_name

        for r in result.get("rankings", []):
            _resolve(r)
        for risk in result.get("risks", []):
            _resolve(risk)
        rec = result.get("recommendation")
        if rec:
            _resolve(rec)
        logger.info("比价分析完成: 排名%d家, 风险%d条",
                     len(result.get("rankings", [])),
                     len(result.get("risks", [])))
        return result
    except (json.JSONDecodeError, KeyError) as e:
        logger.error("比价分析 JSON 解析失败: %s", e)
        return _fallback_analysis(inquiry, quotes)
    except Exception as e:
        logger.error("比价分析异常: %s", e, exc_info=True)
        raise


def generate_award_reason(inquiry: dict, winning_quote: dict, other_quotes: list[dict]) -> str:
    """AI 生成授标理由

    Args:
        inquiry: {category, quantity, unit}
        winning_quote: {supplierCompanyName, totalPrice, currency, deliveryDays, paymentTerms}
        other_quotes: [{supplierCompanyName, totalPrice, currency, deliveryDays}]

    Returns:
        授标理由文本
    """
    other_lines = []
    for q in (other_quotes or []):
        name = q.get("supplierCompanyName", "未知")
        price = q.get("totalPrice", "-")
        currency = q.get("currency", "CNY")
        delivery = q.get("deliveryDays", "-")
        other_lines.append(f"- {name}：{price} {currency}，交期{delivery}天")

    other_text = "\n".join(other_lines) if other_lines else "无其他报价"

    try:
        reason = _call_llm(
            AWARD_REASON_PROMPT,
            max_tokens=256,
            winner_name=winning_quote.get("supplierCompanyName", ""),
            winner_price=str(winning_quote.get("totalPrice", "")),
            winner_currency=winning_quote.get("currency", "CNY"),
            winner_delivery=str(winning_quote.get("deliveryDays", "")),
            winner_payment=winning_quote.get("paymentTerms", "未指定"),
            other_quotes_text=other_text,
            inquiry_category=inquiry.get("category", ""),
            inquiry_quantity=str(inquiry.get("quantity", "")),
            inquiry_unit=inquiry.get("unit", ""),
        )
        logger.info("授标理由生成成功: %s...", reason[:50])
        return reason
    except Exception as e:
        logger.error("授标理由生成异常: %s", e, exc_info=True)
        raise


def suggest_inquiry_fields(title: str, category: str, quantity: str = "", unit: str = "") -> dict:
    """AI 询盘内容辅助

    Args:
        title: 询盘标题
        category: 采购品类
        quantity: 数量
        unit: 单位

    Returns:
        {specTemplate, paymentSuggestions, deliverySuggestions, tips}
    """
    try:
        raw = _call_llm(
            INQUIRY_SUGGEST_PROMPT,
            max_tokens=512,
            title=title or "",
            category=category or "",
            quantity=quantity or "",
            unit=unit or "",
        )
        result = _extract_json(raw)
        logger.info("询盘建议生成成功: category=%s", category)
        return result
    except (json.JSONDecodeError, KeyError) as e:
        logger.error("询盘建议 JSON 解析失败: %s", e)
        return {
            "specTemplate": f"请根据{category}行业标准注明规格参数",
            "paymentSuggestions": ["30%预付款 + 70%货到付款", "100%货到付款", "分期付款（协商确定）"],
            "deliverySuggestions": "建议与供应商协商确定合理交货周期",
            "tips": "建议在规格条款中明确质量标准、验收方式和违约责任",
        }
    except Exception as e:
        logger.error("询盘建议生成异常: %s", e, exc_info=True)
        raise


# ==================== 降级兜底 ====================

def _fallback_analysis(inquiry: dict, quotes: list[dict]) -> dict:
    """LLM 不可用时的规则降级分析。

    降级结果同样保留 ``quoteId``，保证 Java 侧无论拿到 AI 分析还是规则分析，
    都能用同一个稳定 ID 关联真实报价，而不是依赖供应商名称文本。
    """
    sorted_quotes = sorted(quotes, key=lambda q: float(q.get("totalPrice", 0) or 0))

    rankings = []
    for i, q in enumerate(sorted_quotes):
        rankings.append({
            "rank": i + 1,
            "quoteId": q.get("quoteId", 0),
            "supplierName": q.get("supplierCompanyName", ""),
            "score": round(100 - i * 10, 1),
            "strength": "总价最低" if i == 0 else "综合表现尚可",
            "risk": "暂无显著风险",
            "comment": f"按总价排序第{i + 1}名，建议综合考量交期和供应商资质" if i > 0
            else "总价最低，建议优先考虑",
        })

    return {
        "rankings": rankings,
        "risks": [],
        "recommendation": {
            "quoteId": sorted_quotes[0].get("quoteId", 0),
            "supplierName": sorted_quotes[0].get("supplierCompanyName", ""),
            "reason": "总价最低（人工确认后再做最终决策）",
        },
        "summary": f"共收到{len(quotes)}家供应商报价，以上为基于总价的简单排序。建议结合交期、供应商资质等维度人工综合判断。（注：AI 深度分析暂不可用，当前为降级规则分析）",
    }
