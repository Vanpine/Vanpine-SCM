"""
DeepSeek LLM 推理服务
对 Top 10 匹配结果生成中文可读的匹配理由
使用 LangChain + OpenAI 兼容接口调用 DeepSeek V3
"""
import os  # 读取环境变量
import re  # 拆行、去掉编号
import logging  # 日志模块
import threading  # 单例延迟创建的互斥（多线程并发调用）
from concurrent.futures import ThreadPoolExecutor, as_completed  # 并发执行
from langchain_openai import ChatOpenAI  # DeepSeek 兼容 OpenAI API
from langchain_core.prompts import ChatPromptTemplate  # LangChain Prompt 模板

logger = logging.getLogger("srm-ai.reasoning")  # 模块日志器

# DeepSeek API 密钥（仅从环境变量读取，.env 由 main.py 的 load_dotenv() 加载）
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
# DeepSeek API 地址（官方 OpenAI 兼容接口）
DEEPSEEK_BASE_URL = "https://api.deepseek.com/"
# 模型偶发带 1. / - 前缀，拆行时去掉
_LINE_PREFIX = re.compile(r"^(?:[-*•●]|\d+[\.、\)）])\s*")
# 单家供应商最多展示几条 AI 理由
_MAX_REASON_LINES = 8

# 匹配理由生成的 Prompt 模板
MATCH_REASON_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """你是供应链采购专家。根据采购需求与供应商画像，写 4 到 6 条具体匹配理由。
要求：
1. 每条 40～80 字，必须点名双方的具体字段（产品、资质、产能、地域、数量等），禁止空话套话
2. 尽量覆盖这些角度（有数据才写，没数据不要编）：品类/产品对口、资质覆盖、产能与采购量、地域交付、出口或行业经验、合作建议或缺口
3. 综合得分≥80 时，最后一条写清为何建议优先联系；得分偏低时写清主要短板
4. 每条一行，不要编号、不要前缀、不要标题"""),
    ("user", """采购需求：
- 标题：{req_title}
- 品类：{req_category}
- 资质要求：{req_certs}
- 期望地域：{req_region}
- 数量：{req_quantity}{req_unit}
- 预算：{req_budget}
- 描述：{req_desc}

供应商画像：
- 企业：{sup_name}
- 所在地：{sup_location}
- 主营品类：{sup_categories}
- 主要产品：{sup_products}
- 年产能：{sup_capacity}
- 资质认证：{sup_certs}
- 行业：{sup_industry}
- 出口经验：{sup_export_label}

匹配质量数据：
- 综合匹配分：{total_score}（满分100）
- 品类覆盖度：{category_score}%
- 画像完整度：{completeness}%
- 语义相似度：{semantic_score}

匹配理由："""),
])


# 延迟创建的单例：本函数会被 ThreadPoolExecutor 的多线程并发调用，
# 每次新建客户端等于每个线程各开一套连接池，复用后 10 路并发共用一条连接池。
_llm_lock = threading.Lock()
_llm: ChatOpenAI | None = None


def _get_llm() -> ChatOpenAI:
    """返回共享的 DeepSeek LLM 客户端（首次调用创建，之后复用连接池）。"""
    global _llm
    if _llm is None:
        with _llm_lock:
            if _llm is None:
                _llm = ChatOpenAI(
                    model="deepseek-flash",  # DeepSeek 官方 Flash 快速对话模型（V4.1）
                    api_key=DEEPSEEK_API_KEY,  # API 密钥
                    base_url=DEEPSEEK_BASE_URL,  # 自定义 API 地址
                    temperature=0.4,  # 略提高多样性，仍要稳定
                    max_tokens=640,  # 4～6 条理由需要更多字数
                    request_timeout=60,  # 单次请求超时 60 秒，避免 LLM 挂起
                )
    return _llm


def generate_match_reasons(requirement: dict, candidates: list[dict], scores: list[dict] = None) -> list[list[str]]:
    """对每个候选供应商生成多条匹配理由

    Args:
        requirement: 采购需求字典 {title, category, description, ...}
        candidates: 候选供应商字典列表
        scores: 得分数据列表 [{score, semantic_score, category_score, completeness, sup_export}]

    Returns:
        与 candidates 等长的理由列表（每家多条）
    """
    if scores is None:
        scores = [{}] * len(candidates)

    if not DEEPSEEK_API_KEY:  # 未配置 API Key
        logger.warning("DeepSeek API Key 未配置，使用模板理由")
        return [_template_reason_lines(requirement, c, s) for c, s in zip(candidates, scores)]

    logger.info("Step 7/7: DeepSeek 生成匹配理由, candidates=%d (并发)", len(candidates))

    # 并发调用 LLM：10 次串行 → 1 次并发，从 ~15s 降到 ~2s
    with ThreadPoolExecutor(max_workers=min(len(candidates), 10)) as executor:
        futures = {}
        for i, (c, s) in enumerate(zip(candidates, scores)):
            future = executor.submit(_generate_one_reason, requirement, c, s, i)
            futures[future] = i

        # 按原始索引组装结果
        reasons = [[] for _ in candidates]
        for future in as_completed(futures):
            idx, lines = future.result()
            reasons[idx] = lines
            logger.debug("LLM 理由生成成功: candidate=%d/%d, lines=%d",
                         idx + 1, len(candidates), len(lines))

    return reasons


def _generate_one_reason(requirement: dict, candidate: dict, score: dict, idx: int) -> tuple[int, list[str]]:
    """为单个候选供应商生成多条匹配理由（供 ThreadPoolExecutor 并发调用）"""
    export_label_map = {0: "无", 1: "3年以下", 2: "3-5年", 3: "5-10年", 4: "10年以上"}
    llm = _get_llm()  # 每个线程独立的 LLM 客户端
    try:
        # 解析出口档位
        sup_export_raw = candidate.get("sup_export", 0) or 0
        try:
            export_level = int(str(sup_export_raw))
        except (ValueError, TypeError):
            export_level = 0
        sup_export_label = export_label_map.get(export_level, str(export_level))

        location = " ".join(
            part for part in [candidate.get("province", ""), candidate.get("city", "")] if part
        )
        qty = requirement.get("quantity", "") or ""
        unit = requirement.get("unit", "") or ""
        prompt = MATCH_REASON_PROMPT.format_messages(
            req_title=_field(requirement.get("title")),
            req_category=_field(requirement.get("category")),
            req_certs=_field(requirement.get("cert_require")),
            req_region=_field(requirement.get("region")),
            req_quantity=_field(qty),
            req_unit=(" " + unit) if qty and unit else "",
            req_budget=_field(requirement.get("budget")),
            req_desc=_field((requirement.get("description", "") or "")[:800]),
            sup_name=_field(candidate.get("company_name")),
            sup_location=_field(location),
            sup_categories=_field(candidate.get("sup_categories")),
            sup_products=_field(candidate.get("main_products")),
            sup_capacity=_field(candidate.get("sup_capacity")),
            sup_certs=_field(candidate.get("sup_certs")),
            sup_industry=_field(candidate.get("industry")),
            sup_export_label=sup_export_label,
            total_score=f"{score.get('score', 0):.1f}",
            category_score=f"{score.get('category_score', 0):.0f}",
            completeness=f"{score.get('completeness', 0)}",
            semantic_score=f"{score.get('semantic_score', 0):.1f}",
        )
        resp = llm.invoke(prompt)  # 同步调用 LLM
        lines = split_reason_lines(resp.content if resp.content else "")
        return (idx, lines if lines else _template_reason_lines(requirement, candidate, score))
    except Exception as e:
        logger.warning("LLM 理由生成失败 #%d: %s, 降级到模板理由", idx + 1, e)
        return (idx, _template_reason_lines(requirement, candidate, score))


def split_reason_lines(text: str) -> list[str]:
    """把模型输出拆成多条理由，去掉编号和空行。"""
    if not text or not str(text).strip():
        return []
    lines = []
    for raw in str(text).splitlines():
        line = _LINE_PREFIX.sub("", raw.strip()).strip()
        if not line:
            continue
        lines.append(line)
        if len(lines) >= _MAX_REASON_LINES:
            return lines
    if lines:
        return lines
    parts = [p.strip() for p in re.split(r"[；;]", str(text)) if p.strip()]
    return parts[:_MAX_REASON_LINES]


def _field(value) -> str:
    """空字段写成未填写，避免模型脑补。"""
    text = str(value).strip() if value is not None else ""
    return text if text else "未填写"


def _template_reason_lines(requirement: dict, candidate: dict, score: dict = None) -> list[str]:
    """无 LLM 时的模板化回退理由（保证前端始终有内容展示）"""
    if score is None:
        score = {}
    parts = []
    sup_cat = candidate.get("sup_categories", "")
    req_cat = requirement.get("category", "")
    if sup_cat:
        parts.append(f"主营{sup_cat}，与需求品类「{req_cat or '未填写'}」对口")
    sup_certs = candidate.get("sup_certs", "")
    req_certs = requirement.get("cert_require", "")
    if sup_certs:
        parts.append(f"已具备{sup_certs}，可对照需求资质「{req_certs or '不限'}」")
    location = " ".join(
        part for part in [candidate.get("province", ""), candidate.get("city", "")] if part
    )
    req_region = requirement.get("region", "")
    if location:
        parts.append(f"企业位于{location}，需求期望地域为「{req_region or '不限'}」")
    export_label_map = {0: "无", 1: "3年以下", 2: "3-5年", 3: "5-10年", 4: "10年以上"}
    sup_export = candidate.get("sup_export", 0) or 0
    try:
        export_level = int(str(sup_export))
        if export_level >= 2:
            parts.append(f"出口经验{export_label_map.get(export_level, '')}，适合有跨境交付要求的采购")
    except (ValueError, TypeError):
        pass
    sup_products = candidate.get("main_products", "")
    if sup_products:
        parts.append(f"主要产品覆盖{sup_products[:40]}")
    capacity = candidate.get("sup_capacity", "")
    qty = requirement.get("quantity", "")
    unit = requirement.get("unit", "")
    if capacity:
        qty_text = f"{qty}{unit}" if qty else "需求数量未填写"
        parts.append(f"年产能{capacity}，可对照本单采购量{qty_text}评估供货能力")
    sup_industry = candidate.get("industry", "")
    if sup_industry:
        parts.append(f"所属行业为{sup_industry}")
    if not parts:
        total = score.get("score", 0)
        parts.append(f"综合匹配度 {total:.0f} 分，建议作为候选联系")
    return parts[:_MAX_REASON_LINES]


def _template_reason(requirement: dict, candidate: dict, score: dict = None) -> str:
    """兼容旧调用：模板理由拼成一段。"""
    return "；".join(_template_reason_lines(requirement, candidate, score))
