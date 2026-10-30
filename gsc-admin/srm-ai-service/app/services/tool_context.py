"""
工具结果进上下文前的裁剪：字段投影 + 条数上限 + 字符兜底。

为什么必须裁：
  工具结果直接 json.dumps 整包塞进 ToolMessage，是上下文里唯一没有预算约束的部分。
  Java 返回的列表 DTO 带 tenantId / buyerTenantId / createTime / pendingPromiseId
  等模型用不上的字段，10 条就能上千 token；Agent 循环最多 4 轮，历史轮的工具结果
  还会一直背着——不裁的话总上下文随轮数线性增长。

裁剪原则：
  - 列表类只留「回答要用 + 下一轮定位要用」的字段（定位靠 id / 业务编号）；
  - 详情类不裁字段，只受字符上限约束（详情本身是用户明确问的那一条）；
  - entities 不进模型：它已经随 tool_result 事件回前端，并落 ai_conversations.context_entities，
    下一轮指代消解由 Java 侧完成，模型不需要再看一遍。
"""
import json
import logging

logger = logging.getLogger("srm-ai.tool_context")

# 单条工具结果送模型的字符上限（硬兜底，防未知工具/异常大对象）
MAX_TOOL_RESULT_CHARS = 6000

# 列表类工具进上下文的最多条数（超出给「共 N 条」提示，让模型知道要缩小范围）
MAX_ITEMS_IN_CONTEXT = 10

# 每个工具进上下文的字段白名单；不在表里的工具走「详情类」逻辑（不裁字段，只受字符上限）
TOOL_FIELD_PROJECTIONS = {
    # 合同列表：编号 + 标题 + 厂商 + 金额 + 状态 + 交期
    "list_contracts": [
        "id", "contractNo", "title", "amount", "currency",
        "statusText", "deliveryDate", "signDate",
    ],
    # 询盘列表：编号 + 标题 + 品类数量 + 截止 + 状态
    "list_inquiries": [
        "id", "inquiryNo", "title", "category", "quantity", "unit",
        "statusText", "quoteDeadline", "deliveryDate", "paymentTerms",
    ],
    # 交付单列表：单号 + 合同关联 + 厂商 + 状态 + 运单 + 交期
    "list_deliveries": [
        "id", "deliveryNo", "contractNo", "contractTitle", "inquiryNo",
        "supplierCompanyName", "statusText", "quantity",
        "shipCompany", "shipTrackingNo", "deliveryDate",
        "confirmedDeliveryDate", "latestLogisticsEvent", "latestEta",
    ],
    # 供应商搜索：租户 ID（后续查详情/记备注要用）+ 企业名 + 行业品类 + 地区 + 准入
    "search_suppliers": [
        "tenantId", "companyName", "industry", "supCategories",
        "province", "city", "supCerts", "accessStatus",
    ],
}


def project_tool_result(name: str, result: dict) -> str:
    """把一次工具执行结果压成送模型的 JSON 字符串。

    Args:
        name: 工具名
        result: Java /internal/ai/tool 返回的完整响应（success/result/error/entities...）

    Returns:
        送进 ToolMessage 的字符串；永不超过 MAX_TOOL_RESULT_CHARS
    """
    if not isinstance(result, dict):
        return _hard_cap(json.dumps(result, ensure_ascii=False))

    # 失败：只留错误语义，不回整个响应体（entities 等字段对模型无意义）
    if not result.get("success"):
        return json.dumps(
            {"success": False, "error": result.get("error") or "工具执行失败"},
            ensure_ascii=False,
        )

    data = result.get("result")
    fields = TOOL_FIELD_PROJECTIONS.get(name)

    # 列表类：字段投影 + 条数上限 + 总数提示
    if fields and isinstance(data, list):
        items = [
            {k: it.get(k) for k in fields if it.get(k) is not None}
            for it in data[:MAX_ITEMS_IN_CONTEXT]
            if isinstance(it, dict)
        ]
        payload = {"items": items, "total": len(data)}
        if len(data) > MAX_ITEMS_IN_CONTEXT:
            payload["note"] = (
                f"仅展示前 {MAX_ITEMS_IN_CONTEXT} 条，共 {len(data)} 条；"
                "需要更多请缩小查询条件（关键词/状态）"
            )
        return _hard_cap(json.dumps(payload, ensure_ascii=False))

    # 详情类 / 天气等 dict 结果：不裁字段，只做字符兜底
    return _hard_cap(json.dumps({"success": True, "result": data}, ensure_ascii=False))


def _hard_cap(text: str) -> str:
    """字符上限兜底。截断点加说明，避免模型把半截 JSON 当成完整数据。"""
    if len(text) <= MAX_TOOL_RESULT_CHARS:
        return text
    logger.info("工具结果超字符上限被截断: %d -> %d", len(text), MAX_TOOL_RESULT_CHARS)
    return text[:MAX_TOOL_RESULT_CHARS] + "…[结果过长已截断，如需完整数据请缩小查询范围]"
