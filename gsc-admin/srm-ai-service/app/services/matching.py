"""
智能匹配核心逻辑
流程：需求 Embedding → Milvus 语义召回 → 候选合并 → 硬过滤（品类+资质+产品） → 综合打分 → DeepSeek 理由

硬过滤规则（与 Java RuleBasedMatchStrategy 对齐）：
  1. 品类必须重叠（Jaccard > 0）
  2. 资质必须全部匹配（需求的每项资质都要在供应商中找到，精确比较）

打分规则（总分 100）：
  品类覆盖率 × 0.30 + 资质匹配率 × 0.25 + 产品关键词 × 0.15
  + 地域匹配 × 0.10 + 行业匹配 × 0.05 + 完整度 × 0.10 + 出口 × 0.05
"""
import logging  # 日志模块
import re  # 正则表达式（提取中文关键词）
from app.services.embedding import encode, build_requirement_text, build_profile_text
from app.store.milvus_store import (
    query_similar,
    upsert_embedding,
    upsert_requirement,
    delete_requirement,
    query_similar_requirements,
)
from app.services.reasoning import generate_match_reasons  # DeepSeek 理由生成

logger = logging.getLogger("srm-ai.matching")  # 模块日志器

# ==================== 配置常量 ====================

# 语义召回取 Top 100 个相似供应商
TOP_K_SEMANTIC = 100
# 最终返回前 20 名
TOP_K_FINAL = 20
# DeepSeek 只为 Top 10 生成理由（节省 API 调用成本）
LLM_REASON_TOP = 10
# 最低综合匹配分阈值
MIN_SCORE = 25.0
# 出口经验档位（与前端 SUP_EXPORT_MAP 保持一致）
EXPORT_LABEL = {
    0: "无出口经验",
    1: "3年以下",
    2: "3-5年",
    3: "5-10年",
    4: "10年以上",
}

# ==================== 对外接口 ====================


def embed_profile(tenant_id: int, text: str) -> None:
    """供应商画像向量化并写入 Milvus 库

    Args:
        tenant_id: 租户ID
        text: 拼接后的供应商画像文本
    """
    embedding = encode(text)  # BGE 编码为 1024 维向量
    upsert_embedding(  # 写入向量库
        tenant_id,  # 以租户ID为唯一标识
        embedding,  # 向量数据
        {"tenant_id": str(tenant_id), "text": text[:500]},  # 元数据快照（截断500字符）
    )


def embed_requirement(requirement: dict) -> None:
    """公开需求向量化后写入 open_requirements。"""
    req_id = int(requirement["requirement_id"])
    text = build_requirement_text(requirement)
    if not text.strip():
        raise ValueError("需求文本为空，无法向量化")
    embedding = encode(text)
    upsert_requirement(req_id, embedding, {
        "requirement_id": str(req_id),
        "tenant_id": str(requirement.get("tenant_id") or ""),
        "title": requirement.get("title") or "",
        "category": requirement.get("category") or "",
        "cert_require": requirement.get("cert_require") or "",
        "region": requirement.get("region") or "",
        "description": (requirement.get("description") or "")[:500],
    })


def remove_requirement(requirement_id: int) -> None:
    """公开需求关闭后从向量库删除。"""
    delete_requirement(int(requirement_id))


def match_requirements(profile: dict, candidates: list[dict]) -> list[dict]:
    """用本厂画像给公开需求打分：SQL 候选 + Milvus 补语义，硬过滤后 Top20。不调 LLM。"""
    profile_text = build_profile_text(profile)
    qdrant_dist = {}
    if profile_text.strip():
        try:
            qdrant_hits = query_similar_requirements(encode(profile_text), top_k=TOP_K_SEMANTIC)
            qdrant_dist = {h["requirement_id"]: h["distance"] for h in qdrant_hits}
        except Exception as e:
            logger.warning("找需求语义召回失败，仅用 SQL 候选: %s", e)
            qdrant_hits = []
    else:
        qdrant_hits = []

    merged = {}
    for c in candidates:
        rid = int(c["requirement_id"])
        merged[rid] = {
            "requirement_id": rid,
            "title": c.get("title") or "",
            "category": c.get("category") or "",
            "cert_require": c.get("cert_require") or "",
            "region": c.get("region") or "",
            "description": c.get("description") or "",
        }
    for hit in qdrant_hits:
        rid = hit["requirement_id"]
        if rid in merged:
            continue
        meta = hit.get("metadata") or {}
        merged[rid] = {
            "requirement_id": rid,
            "title": meta.get("title") or "",
            "category": meta.get("category") or "",
            "cert_require": meta.get("cert_require") or "",
            "region": meta.get("region") or "",
            "description": meta.get("description") or "",
        }

    sup_cat = profile.get("sup_categories") or ""
    sup_cert = profile.get("sup_certs") or ""
    sup_product = profile.get("main_products") or ""
    province = profile.get("province") or ""
    city = profile.get("city") or ""
    industry = profile.get("industry") or ""
    completeness = int(profile.get("completeness_score") or 0)
    export_level = _export_score(profile.get("sup_export", "0") or "0")

    scored = []
    for rid, req in merged.items():
        req_cat = req.get("category") or ""
        if req_cat:
            category_score = _jaccard_score(req_cat, sup_cat)
            if category_score == 0:
                continue
        else:
            category_score = 0.0

        req_certs = req.get("cert_require") or ""
        if req_certs:
            cert_ratio, _ = _cert_match(req_certs, sup_cert)
            if cert_ratio < 1.0:
                continue
        else:
            cert_ratio = 1.0

        req_title = req.get("title") or ""
        req_desc = req.get("description") or ""
        keywords = _extract_keywords(req_cat) | _extract_keywords(req_title)
        product_score = _product_keyword_score(keywords, sup_product) if keywords else 50.0
        req_region = req.get("region") or ""
        if req_region.strip() in ("", "不限"):
            region_score = 50.0
        else:
            region_score = _region_score(req_region, province, city)
        industry_score = _industry_score(req_cat, req_title, req_desc, industry)
        semantic_score = _semantic_score(rid, qdrant_dist)

        total = round(
            category_score * 0.30
            + cert_ratio * 100 * 0.25
            + product_score * 0.15
            + region_score * 0.10
            + industry_score * 0.05
            + completeness * 0.10
            + export_level * 0.05,
            1,
        )
        if total < MIN_SCORE:
            continue
        scored.append({
            "requirement_id": rid,
            "score": total,
            "semantic_score": round(semantic_score, 1),
        })

    scored.sort(key=lambda x: x["score"], reverse=True)
    return scored[:TOP_K_FINAL]


def match(requirement: dict, candidates: list[dict], need_llm_reasons: bool = False) -> list[dict]:
    """执行 AI 智能匹配

    步骤：
    1. 需求文本 → Embedding
    2. Milvus 语义召回 Top 100
    3. 与 Java 传入的候选池合并去重
    4. 三重硬过滤（品类/资质/产品）
    5. 七维综合打分
    6. 按分数降序取 Top 20
    7. 仅 need_llm_reasons=True 时才调 DeepSeek；交互推荐默认关，用模板理由

    Args:
        requirement: 需求字段字典 {title, category, cert_require, region, description, ...}
        candidates: Java 传入的候选供应商列表 [{tenant_id, company_name, sup_categories, ...}]
        need_llm_reasons: 是否为 Top10 调 LLM（默认 False，避免把用户点按钮卡住十几秒）

    Returns:
        [{"tenant_id": int, "score": float, "semantic_score": float, "reasons": [str]}]
    """
    logger.info("开始智能匹配: candidates=%d", len(candidates))

    # Step 1: 需求文本 Embedding
    req_text = build_requirement_text(requirement)  # 标题+品类+描述800字+资质，不含数量预算
    logger.info("Step 1/7: 需求文本编码, text_len=%d, preview=%s", len(req_text), req_text[:100])
    req_embedding = encode(req_text)  # BGE 编码
    logger.info("Step 1/7 完成: 向量维度=%d", len(req_embedding))

    # Step 2: Milvus 语义召回
    logger.info("Step 2/7: Milvus 语义召回, top_k=%d", TOP_K_SEMANTIC)
    qdrant_results = query_similar(req_embedding, top_k=TOP_K_SEMANTIC)
    logger.info("Step 2/7 完成: 召回 %d 条", len(qdrant_results))
    qdrant_dist_map = {r["tenant_id"]: r["distance"] for r in qdrant_results}

    # Step 3: 合并候选池（Java 候选 + Milvus 召回），按 tenant_id 去重
    logger.info("Step 3/7: 合并候选池, Java候选=%d, Milvus召回=%d", len(candidates), len(qdrant_results))
    merged = {}  # {tenant_id: candidate_dict}
    for c in candidates:  # Java 传入的候选优先
        tid = c["tenant_id"]  # 提取租户ID
        if tid not in merged:  # 未重复
            merged[tid] = c  # 加入合并池
    for r in qdrant_results:
        tid = r["tenant_id"]  # 提取租户ID
        if tid not in merged:  # 新供应商（未在 Java 候选池中，可能画像未同步）
            merged[tid] = {  # 用向量库元数据构造最小候选对象
                "tenant_id": tid,
                "company_name": r["metadata"].get("company_name", ""),
                "sup_categories": r["metadata"].get("sup_categories", ""),
                "sup_capacity": "",
                "sup_certs": "",
                "sup_export": "0",
                "completeness_score": 0,
                "main_products": "",
                "industry": r["metadata"].get("industry", ""),
                "province": "",
                "city": "",
            }

    # 预提取需求字段
    req_category = requirement.get("category", "") or ""  # 需求品类
    req_certs = requirement.get("cert_require", "") or ""  # 需求资质
    req_region = requirement.get("region", "") or ""  # 需求地域
    req_title = requirement.get("title", "") or ""  # 需求标题
    req_desc = requirement.get("description", "") or ""  # 需求描述

    # Step 4-5: 硬过滤 + 综合打分
    filtered_count = 0  # 过滤计数
    scored = []  # [(score, result_dict)]
    for tid, c in merged.items():  # 遍历合并后的每个供应商
        sup_cat = c.get("sup_categories", "") or ""  # 供应商品类
        sup_cert = c.get("sup_certs", "") or ""  # 供应商资质
        sup_product = c.get("main_products", "") or ""  # 供应商主要产品

        # ---- 4a. 品类硬过滤：需求有品类时，Jaccard 必须 > 0 ----
        if req_category:  # 需求指定了品类
            jaccard_score = _jaccard_score(req_category, sup_cat)  # 计算品类重叠
            if jaccard_score == 0:  # 品类零重叠 → 必然不相关
                logger.debug("品类硬过滤: 跳过 tenantId=%s, 需求=%s, 供应商=%s",
                             tid, req_category[:50], sup_cat[:50])
                filtered_count += 1
                continue  # 跳过该供应商
        else:
            jaccard_score = 0  # 需求无品类，品类分=0

        # ---- 4b. 资质硬过滤：需求的每项资质都要在供应商中找到（精确 equal） ----
        if req_certs:  # 需求有资质要求
            cert_match_ratio, matched_certs = _cert_match(req_certs, sup_cert)  # 计算资质匹配
            if cert_match_ratio < 1.0:  # 未全部匹配 → 不满足硬性要求
                logger.debug("资质硬过滤: 跳过 tenantId=%s, 需求资质=%s, 供应商资质=%s",
                             tid, req_certs[:50], sup_cert[:50])
                filtered_count += 1
                continue  # 跳过该供应商
        else:
            cert_match_ratio = 1.0  # 无资质要求 → 满分
            matched_certs = []

        # ---- 4c. 产品关键词评分（纯打分，不过滤） ----
        product_keywords = _extract_keywords(req_category) | _extract_keywords(req_title)  # 需求关键词
        product_score = _product_keyword_score(product_keywords, sup_product) if product_keywords else 50.0

        # ---- 5. 七维综合打分 ----
        # 品类分（已在 4a 计算）
        category_score = jaccard_score  # 0-100

        # 资质分（已在 4b 计算，此处复用 matched 数量）
        cert_score = cert_match_ratio * 100  # 0-100

        # 产品分（已在 4c 计算）
        # product_score 0-100

        # 地域匹配分
        province = c.get("province", "") or ""
        city = c.get("city", "") or ""
        region_score = _region_score(req_region, province, city)  # 0-100

        # 行业匹配分
        supplier_industry = c.get("industry", "") or ""
        industry_score = _industry_score(req_category, req_title, req_desc, supplier_industry)  # 0-100

        # 完整度分
        completeness = int(c.get("completeness_score", 0) or 0)  # 0-100

        # 出口经验分
        export_level = _export_score(c.get("sup_export", "0") or "0")  # 0-100

        # 语义分（从向量库距离计算）
        semantic_score = _semantic_score(tid, qdrant_dist_map)

        # 加权求和
        total = round(
            category_score * 0.30    # 品类重叠权重 30%
            + cert_score * 0.25      # 资质匹配权重 25%
            + product_score * 0.15   # 产品关键词权重 15%
            + region_score * 0.10    # 地域匹配权重 10%
            + industry_score * 0.05  # 行业匹配权重 5%
            + completeness * 0.10    # 画像完整度权重 10%
            + export_level * 0.05,   # 出口经验权重 5%
            1,  # 保留 1 位小数
        )

        # ---- 5b. 最低分阈值过滤 ----
        if total < MIN_SCORE:
            logger.debug("最低分过滤: tenantId=%s, score=%.1f < %.0f", tid, total, MIN_SCORE)
            filtered_count += 1
            continue

        # 组装模板化理由
        reasons = []
        if category_score > 50:
            reasons.append(f"品类高度匹配 ({category_score:.0f}分)")
        elif category_score > 0:
            reasons.append(f"品类部分匹配 ({category_score:.0f}分)")
        if matched_certs:
            reasons.append(f"资质匹配：{'、'.join(matched_certs[:5])}")
        if product_score > 60:
            reasons.append(f"产品高度相关 ({product_score:.0f}分)")
        location = " ".join(part for part in [province, city] if part)
        if location and region_score > 50:
            reasons.append(f"位于{location}，便于按需求地域交付")
        elif region_score > 50:
            reasons.append(f"地域匹配 ({region_score:.0f}分)")
        if industry_score > 50:
            reasons.append(f"行业匹配 ({industry_score:.0f}分)")
        if completeness > 60:
            reasons.append(f"画像完整 ({completeness}分)")
        capacity = c.get("sup_capacity", "") or ""
        if capacity:
            reasons.append(f"年产能：{capacity}")
        sup_export_level = int(c.get("sup_export", 0) or 0)  # 出口档位
        if sup_export_level >= 3:  # 5年以上算经验丰富
            reasons.append(f"出口经验丰富（{EXPORT_LABEL.get(sup_export_level, str(sup_export_level))})")

        scored.append({  # 加入评分列表
            "tenant_id": tid,
            "score": total,
            "semantic_score": round(semantic_score, 1),
            "reasons": reasons,
            "candidate": c,  # 保留原始候选数据供 LLM 使用
        })

    logger.info("Step 4-5 完成: 硬过滤掉 %d 家, 剩余 %d 家进入评分",
                filtered_count, len(scored))

    # Step 6: 按得分降序排序，取 Top 20
    scored.sort(key=lambda x: x["score"], reverse=True)  # 从高到低
    top = scored[:TOP_K_FINAL]  # 截取前 20
    logger.info("Step 6/7 完成: 最终 %d 条结果, 最高分=%.1f", len(top),
                top[0]["score"] if top else 0)

    # Step 7: 交互推荐默认跳过 DeepSeek（模板理由已在 Step 5 写好）
    if need_llm_reasons:
        top_for_llm = top[:LLM_REASON_TOP]
        if top_for_llm:
            llm_reasons = generate_match_reasons(
                requirement,
                [s["candidate"] for s in top_for_llm],
                [{"score": s["score"], "semantic_score": s["semantic_score"],
                  "category_score": _jaccard_score(requirement.get("category", ""), s["candidate"].get("sup_categories", "")),
                  "completeness": s["candidate"].get("completeness_score", 0),
                  "sup_export": s["candidate"].get("sup_export", 0)}
                 for s in top_for_llm],
            )
            for i, s in enumerate(top_for_llm):
                extra = llm_reasons[i] if i < len(llm_reasons) else []
                if isinstance(extra, str):
                    extra = [extra] if extra else []
                if extra:
                    s["reasons"].extend(extra)
    else:
        logger.info("Step 7/7 跳过 DeepSeek: 交互匹配用模板理由")

    # 返回纯结果（去掉内部 candidate 数据，只保留前端需要的字段）
    return [
        {
            "tenant_id": s["tenant_id"],  # 供应商ID
            "score": s["score"],  # 综合匹配分
            "semantic_score": s["semantic_score"],  # 语义原始分
            "reasons": s["reasons"],  # 匹配理由
        }
        for s in top  # 遍历 Top 20
    ]


# ==================== 硬过滤函数 ====================

def _jaccard_score(req_category: str, sup_category: str) -> float:
    """品类覆盖率 × 100

    用覆盖率而非 Jaccard：采购方关心"我的需求你能覆盖多少"，供应商额外的品类不应扣分。
    公式：|需求∩供应| / |需求| × 100

    例：需求=钢材,冷轧板(2项)，供应商=钢材,冷轧板,热轧板,镀锌板(4项)
        Jaccard = 2/4 = 50（不合理，供应商多品类反被惩罚）
        覆盖率   = 2/2 = 100（合理，需求全被满足）
    """
    if not req_category or not sup_category:  # 任一方无品类信息
        return 0.0
    req_set = _split_categories(req_category)  # 需求品类集合
    sup_set = _split_categories(sup_category)  # 供应商品类集合
    if not req_set:  # 需求拆分后为空
        return 0.0
    if not sup_set:  # 供应商无品类
        return 0.0
    intersection = req_set & sup_set  # 交集
    return len(intersection) / len(req_set) * 100  # 覆盖率 × 100


def _cert_match(req_cert: str, sup_cert: str) -> tuple[float, list[str]]:
    """资质匹配检验 — 需求的每项资质都要在供应商中找到（精确 equal，忽略大小写）

    与 Java RuleBasedMatchStrategy.passCertFilter 逻辑一致

    Args:
        req_cert: 需求资质，逗号/顿号分隔 如 "ISO9001,CE认证"
        sup_cert: 供应商资质，逗号/顿号分隔 如 "ISO9001,CE认证,RoHS"

    Returns:
        (匹配率 0.0~1.0, 匹配上的资质列表)
    """
    if not req_cert or not req_cert.strip():  # 需求无资质要求
        return 1.0, ["无资质硬性要求"]
    if not sup_cert or not sup_cert.strip():  # 供应商无资质
        return 0.0, []

    req_list = _split_list(req_cert)  # 需求资质列表
    sup_set = _split_categories(sup_cert)  # 供应商资质集合

    matched = []  # 匹配上的资质
    for rc in req_list:  # 遍历每项需求资质
        trimmed = rc.strip()  # 去空白
        if not trimmed:  # 跳过空字符串
            continue
        # 在供应商资质中精确查找（忽略大小写）
        if any(s.strip().lower() == trimmed.lower() for s in sup_set):  # 精确匹配
            matched.append(trimmed)  # 记录匹配项

    ratio = len(matched) / len(req_list) if req_list else 1.0  # 匹配率
    return ratio, matched


# ==================== 打分函数 ====================

def _semantic_score(tenant_id: int, distance_map: dict[int, float]) -> float:
    """余弦距离 → 0-100 分（距离越小分越高）

    余弦距离范围 [0, 2]：0=完全相同，2=完全相反
    转换公式: (1 - distance/2) * 100
    """
    dist = distance_map.get(tenant_id)  # 查找该供应商的余弦距离
    if dist is None:  # 不在向量库召回结果中
        return 20.0  # 给一个低分兜底（不加到总评分的加权中）
    return max(0, (1.0 - dist / 2.0)) * 100  # 线性映射到 0-100


def _region_score(req_region: str, province: str, city: str) -> float:
    """地域匹配分

    - 需求无地域要求 → 50（中性）
    - 精确匹配城市 → 100
    - 匹配省份 → 75
    - 不匹配 → 0
    """
    if not req_region:  # 无地域要求
        return 50.0
    req = req_region.strip().lower()
    if city and req in city.lower():  # 城市精确匹配
        return 100.0
    if province and req in province.lower():  # 省份匹配
        return 75.0
    return 0.0  # 不匹配


def _industry_score(req_category: str, req_title: str, req_desc: str,
                    sup_industry: str) -> float:
    """行业匹配分 — 关键词在供应商行业中出现则加分"""
    if not sup_industry:  # 供应商无行业信息
        return 0.0
    # 从需求品类和标题提取行业关键词
    keywords = _extract_keywords(req_category) | _extract_keywords(req_title) | _extract_keywords(req_desc[:200])
    if not keywords:  # 无有效关键词
        return 30.0  # 中性分
    sup_lower = sup_industry.lower()
    hits = sum(1 for kw in keywords if kw.lower() in sup_lower)  # 命中数
    if hits == 0:
        return 0.0
    return min(hits / max(len(keywords), 1) * 100, 100.0)  # 命中率 × 100


def _product_keyword_score(keywords: set[str], sup_product: str) -> float:
    """产品关键词命中分"""
    if not sup_product or not keywords:
        return 30.0  # 中性分
    sup_lower = sup_product.lower()
    hits = sum(1 for kw in keywords if kw.lower() in sup_lower)  # 命中数
    if hits == 0:
        return 0.0
    return min(hits / max(len(keywords), 1) * 100, 100.0)  # 命中率 × 100


def _export_score(sup_export: str) -> float:
    """出口经验等级 → 0-100 分

    0=无出口经验 → 0, 4=10年以上 → 100
    与前端 SUP_EXPORT_MAP 保持一致
    """
    try:
        level = int(str(sup_export).strip())  # 解析等级
        return level / 4.0 * 100  # 0-4 映射到 0-100
    except (ValueError, AttributeError):  # 非数字或空值
        return 0.0  # 兜底 0 分


# ==================== 工具函数 ====================

def _split_categories(text: str) -> set[str]:
    """按逗号/中文逗号/顿号分割品类，返回去重集合"""
    result = set()  # 去重集合
    for part in re.split(r"[,，、]", str(text)):  # 按分隔符拆分
        trimmed = part.strip()  # 去空白
        if trimmed:  # 非空
            result.add(trimmed)  # 加入集合
    return result


def _split_list(text: str) -> list[str]:
    """按逗号/中文逗号/顿号分割，返回列表（保持顺序，不去重）"""
    result = []  # 结果列表
    for part in re.split(r"[,，、]", str(text)):  # 按分隔符拆分
        trimmed = part.strip()  # 去空白
        if trimmed:  # 非空
            result.append(trimmed)  # 加入列表
    return result


def _extract_keywords(text: str) -> set[str]:
    """从文本中提取中文关键词（2字及以上）"""
    if not text:
        return set()
    # 去掉标点符号，保留中文和字母数字
    cleaned = re.sub(r"[^一-龥a-zA-Z0-9]", " ", str(text))
    # 按空格和分隔符分词，取 >=2 字的关键词
    keywords = set()
    for word in re.split(r"[\s,，、]+", cleaned):
        word = word.strip()
        if len(word) >= 2:  # 至少 2 个字符
            keywords.add(word)
    return keywords
