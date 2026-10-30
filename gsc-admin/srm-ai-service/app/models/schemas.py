"""
Pydantic 请求/响应数据模型
遵循 PEP 8 规范，所有字段使用 snake_case 命名
"""
from pydantic import BaseModel, Field  # Pydantic v2 数据校验框架


# ==================== /embed — 供应商画像向量化接口 ====================

class EmbedRequest(BaseModel):
    """供应商画像向量化请求体"""
    tenant_id: int = Field(..., description="租户ID，作为向量库中的唯一标识")  # 租户主键
    text: str = Field(..., description="拼接后的供应商画像文本（公司名+品类+产品+行业+简介）")  # 待向量化的文本


class EmbedResponse(BaseModel):
    """供应商画像向量化响应体"""
    tenant_id: int = Field(..., description="租户ID")  # 回显租户ID
    status: str = Field(default="ok", description="处理状态：ok 或 error")  # 操作结果
    message: str = Field(default="", description="错误信息（成功时为空）")  # 附加说明


# ==================== /match — AI 智能匹配接口 ====================

class RequirementInfo(BaseModel):
    """采购需求信息（由 Java 后端传入）"""
    title: str = Field(..., description="需求标题")  # 需求名称
    category: str = Field(..., description="采购品类，逗号分隔")  # 如 "电子元器件,PCB"
    cert_require: str = Field(default="", description="资质要求")  # 如 "ISO9001,CE认证"
    region: str = Field(default="", description="期望供应商地域，空=不限")  # 如 "广东"
    description: str = Field(default="", description="需求详细描述")  # AI 语义分析核心字段
    quantity: str = Field(default="", description="采购数量")  # 如 "1000"
    unit: str = Field(default="", description="单位")  # 如 "件"
    budget: str = Field(default="", description="预算描述")  # 如 "500万-800万"


class CandidateInfo(BaseModel):
    """候选供应商信息（由 Java 后端传入）"""
    tenant_id: int = Field(..., description="供应商租户ID")  # 唯一标识
    company_name: str = Field(default="", description="公司名称")  # 企业名
    sup_categories: str = Field(default="", description="生产品类，逗号分隔")  # 如 "电子元器件,连接器"
    sup_capacity: str = Field(default="", description="年产能描述")  # 产能文本
    sup_certs: str = Field(default="", description="资质认证，逗号分隔")  # 如 "ISO9001,CE"
    sup_export: str = Field(default="", description="出口经验等级 (0-4)")  # 0=无, 4=10年以上
    completeness_score: int = Field(default=0, description="画像完整度评分 (0-100)")  # 数据质量指标
    main_products: str = Field(default="", description="主要产品/服务")  # 主营产品
    industry: str = Field(default="", description="所属行业")  # 行业分类
    province: str = Field(default="", description="省份")  # 所在省份
    city: str = Field(default="", description="城市")  # 所在城市


class MatchRequest(BaseModel):
    """AI 智能匹配请求体"""
    requirement: RequirementInfo = Field(..., description="采购需求")  # 匹配目标
    candidates: list[CandidateInfo] = Field(..., description="候选供应商列表")  # 候选池
    # 交互推荐默认关闭：模板理由已够展示，10 路 DeepSeek 会把接口拖到数秒～十几秒
    need_llm_reasons: bool = Field(default=False, description="是否为 Top10 调用 DeepSeek 生成理由")


class MatchResult(BaseModel):
    """单个供应商的匹配结果"""
    tenant_id: int = Field(..., description="供应商租户ID")  # 唯一标识
    score: float = Field(..., description="综合匹配分 (0-100)")  # 降序排列依据
    semantic_score: float = Field(..., description="语义相似度原始分 (0-100)")  # Embedding 相似度
    reasons: list[str] = Field(default_factory=list, description="匹配理由列表")  # 人类可读的解释


class MatchResponse(BaseModel):
    """AI 智能匹配响应体"""
    results: list[MatchResult] = Field(default_factory=list, description="匹配结果列表，按 score 降序")  # 排序后的结果


class RequirementEmbedRequest(BaseModel):
    """公开需求向量化请求"""
    requirement_id: int = Field(..., description="需求ID")
    title: str = Field(default="", description="标题")
    category: str = Field(default="", description="品类")
    description: str = Field(default="", description="描述")
    cert_require: str = Field(default="", description="资质")
    region: str = Field(default="", description="地域")
    tenant_id: int = Field(default=0, description="发布方租户ID")


class SupplierProfileInfo(BaseModel):
    """找需求时的本厂画像"""
    company_name: str = Field(default="")
    sup_categories: str = Field(default="")
    sup_certs: str = Field(default="")
    sup_export: str = Field(default="0")
    completeness_score: int = Field(default=0)
    main_products: str = Field(default="")
    industry: str = Field(default="")
    description: str = Field(default="")
    province: str = Field(default="")
    city: str = Field(default="")


class RequirementCandidateInfo(BaseModel):
    """SQL 召回的公开需求"""
    requirement_id: int
    title: str = Field(default="")
    category: str = Field(default="")
    cert_require: str = Field(default="")
    region: str = Field(default="")
    description: str = Field(default="")


class RequirementMatchResult(BaseModel):
    requirement_id: int
    score: float
    semantic_score: float = 0


class MatchRequirementsRequest(BaseModel):
    profile: SupplierProfileInfo
    candidates: list[RequirementCandidateInfo] = Field(default_factory=list)


class MatchRequirementsResponse(BaseModel):
    results: list[RequirementMatchResult] = Field(default_factory=list)


# ==================== /risk/assess — 风险预测接口 (Phase 2 预留) ====================

class RiskRequest(BaseModel):
    """风险预测请求体"""
    tenant_id: int = Field(..., description="供应商租户ID")  # 待评估的供应商


class RiskResponse(BaseModel):
    """风险预测响应体"""
    tenant_id: int = Field(..., description="供应商租户ID")  # 回显
    risk_level: str = Field(default="low", description="风险等级：low / medium / high")  # 风险分类
    score: float = Field(default=0.0, description="风险评分 (0-100，越高越危险)")  # 量化风险
    summary: str = Field(default="", description="风险分析摘要")  # 人类可读的风险说明


# ==================== /inventory/alert — 库存预警接口 (Phase 2 预留) ====================

class InventoryAlertRequest(BaseModel):
    """库存预警请求体"""
    category: str = Field(..., description="采购品类")  # 待分析的品类


class InventoryAlertResponse(BaseModel):
    """库存预警响应体"""
    category: str = Field(..., description="品类名称")  # 回显
    demand_count: int = Field(default=0, description="近30天需求数量")  # 需求侧统计
    supplier_count: int = Field(default=0, description="该品类供应商数量")  # 供给侧统计
    ratio: float = Field(default=0.0, description="供需比（需求数/供应商数）")  # 供需平衡指标
    alert: str = Field(default="", description="预警建议")  # 人类可读的预警信息


# ==================== /procurement/analyze-quotes — AI 比价分析接口 ====================

class QuoteInfo(BaseModel):
    """单条报价信息（Java 后端传入）"""
    quoteId: int = Field(..., description="报价ID，用于 AI 结果与真实报价的稳定关联")
    supplierCompanyName: str = Field(default="", description="供应商公司名")
    totalPrice: float = Field(default=0, description="总价")
    unitPrice: float = Field(default=0, description="单价")
    currency: str = Field(default="CNY", description="币种")
    deliveryDays: int = Field(default=0, description="交期（天）")
    deliveryTerms: str = Field(default="", description="交货条款")
    paymentTerms: str = Field(default="", description="付款条件（报价无此字段）")
    validUntil: str = Field(default="", description="报价有效期")
    remark: str = Field(default="", description="报价说明")
    completenessScore: int = Field(default=0, description="供应商画像完整度 (0-100)")


class InquiryInfo(BaseModel):
    """询盘信息（Java 后端传入）"""
    inquiryNo: str = Field(default="", description="询盘编号")
    title: str = Field(default="", description="询盘标题")
    category: str = Field(default="", description="采购品类")
    quantity: str = Field(default="", description="采购数量")
    unit: str = Field(default="", description="单位")
    deliveryPlace: str = Field(default="", description="交货地点")
    paymentTerms: str = Field(default="", description="付款条件")
    quoteDeadline: str = Field(default="", description="报价截止时间")


class QuoteAnalysisRequest(BaseModel):
    """AI 比价分析请求体"""
    inquiry: InquiryInfo = Field(..., description="询盘信息")
    quotes: list[QuoteInfo] = Field(default_factory=list, description="报价列表")


class RankingItem(BaseModel):
    """排名条目"""
    rank: int = Field(..., description="排名")
    quoteId: int = Field(default=0, description="关联报价ID；LLM 未返回时为 0")
    supplierName: str = Field(default="", description="供应商名")
    score: float = Field(default=0.0, description="综合得分")
    strength: str = Field(default="", description="优势")
    risk: str = Field(default="", description="风险")
    comment: str = Field(default="", description="点评")


class RiskItem(BaseModel):
    """风险条目"""
    type: str = Field(default="", description="风险类型：abnormal_low_price|abnormal_short_delivery|expiring_soon")
    quoteId: int = Field(default=0, description="关联报价ID；LLM 未返回时为 0")
    supplierName: str = Field(default="", description="相关供应商名")
    description: str = Field(default="", description="风险描述")


class Recommendation(BaseModel):
    """授标建议"""
    quoteId: int = Field(default=0, description="推荐报价ID；LLM 未返回时为 0")
    supplierName: str = Field(default="", description="推荐供应商名")
    reason: str = Field(default="", description="推荐理由")


class QuoteAnalysisResponse(BaseModel):
    """AI 比价分析响应体"""
    rankings: list[RankingItem] = Field(default_factory=list, description="综合排名")
    risks: list[RiskItem] = Field(default_factory=list, description="风险提示")
    recommendation: Recommendation = Field(default_factory=Recommendation, description="授标建议")
    summary: str = Field(default="", description="整体总结")


# ==================== /procurement/award-reason — AI 授标理由接口 ====================

class AwardReasonRequest(BaseModel):
    """授标理由生成请求体"""
    inquiry: InquiryInfo = Field(..., description="询盘信息")
    winningQuote: QuoteInfo = Field(..., description="中标报价")
    otherQuotes: list[QuoteInfo] = Field(default_factory=list, description="其他报价")


class AwardReasonResponse(BaseModel):
    """授标理由生成响应体"""
    reason: str = Field(default="", description="AI 生成的授标理由")


# ==================== /procurement/inquiry-suggest — AI 询盘辅助接口 ====================

class InquirySuggestRequest(BaseModel):
    """询盘内容辅助请求体"""
    title: str = Field(default="", description="询盘标题")
    category: str = Field(..., description="采购品类")
    quantity: str = Field(default="", description="数量")
    unit: str = Field(default="", description="单位")


class InquirySuggestResponse(BaseModel):
    """询盘内容辅助响应体"""
    specTemplate: str = Field(default="", description="规格条款模板")
    paymentSuggestions: list[str] = Field(default_factory=list, description="付款条件建议")
    deliverySuggestions: str = Field(default="", description="交货周期建议")
    tips: str = Field(default="", description="采购注意事项")


# ==================== /chat — AI 对话机器人接口 ====================

class ChatImage(BaseModel):
    """对话附带的图片（Java 后端现签的 OSS 短效 URL）"""
    # 用 str + max_length 而非 HttpUrl：HttpUrl 解析后不是 str 子类，
    # 直接塞进多模态块的 {"url": ...} 会序列化失败。长度上限与 Java
    # MAX_IMAGE_URL_LEN 对齐（Java 超限直接拒，这里只做防御性兜底）。
    url: str = Field(..., min_length=1, max_length=8192, description="可公网访问的图片地址（http/https）")  # 模型侧直接拉取


class ImageDescription(BaseModel):
    """单张历史图片的内容描述（Java 侧生成，历史轮次替代原图）"""
    index: int = Field(default=0, ge=0, le=99, description="该图在本条消息中的序号（1 起，与用户看到的顺序一致）")  # 让「第 N 张」可对上
    imageId: str = Field(default="", max_length=64, description="图片ID（仅追溯用，模型不消费）")  # 雪花转字符串
    description: str = Field(default="", max_length=500, description="图片内容描述")  # 多模态模型生成的一句话


class ChatMessage(BaseModel):
    """对话历史中的单条消息"""
    role: str = Field(..., max_length=16, description="角色：user / assistant")  # 消息角色
    # 上限 20000：Java 侧按 token 卡（提问 2000 token），中英混排下字符数可达上万，
    # 这里只做「明显异常」的兜底，不替代 Java 的业务校验
    content: str = Field(default="", max_length=20000, description="消息内容")  # 消息文本；纯图片历史可为空
    images: list[ChatImage] = Field(default_factory=list, max_length=4, description="该条历史消息附带的图片（最多 4 张）")  # 多轮读图
    # 图片内容描述（Java 侧首次使用时生成）：历史轮次不带原图（token 太贵），用描述替代，
    # 让「刚才那张图 / 第 2 张」这类追问仍可定位。Pydantic 默认忽略未声明字段——
    # 漏声明这一项会让 Java 传来的描述在 model_dump() 时被静默丢弃，整条链路失效。
    imageDescriptions: list[ImageDescription] = Field(
        default_factory=list, max_length=4,
        description="该条消息所附图片的内容描述（Java 生成，历史轮次替代原图）")


class ChatRequest(BaseModel):
    """对话请求体（Java 后端组装后传入）"""
    question: str = Field(default="", max_length=20000, description="当前用户提问（纯图片提问时可为空）")  # 本次提问
    tenant_id: int = Field(..., description="租户ID（知识库检索隔离 + 上下文）")  # 租户标识
    # 上限 50 与 Java HISTORY_FETCH_LIMIT 对齐；再多说明上游算错了，直接拒比静默吃掉好
    history: list[ChatMessage] = Field(default_factory=list, max_length=50, description="历史消息（升序，最近 N 轮）")  # 上下文
    images: list[ChatImage] = Field(default_factory=list, max_length=4, description="本轮提问附带的图片（最多 4 张）")  # 多模态输入
    tool_token: str = Field(default="", max_length=4096, description="一次性工具令牌（Java 签发，回调工具 API 时鉴权用；空则不启用工具）")  # 工具调用凭证
    # 会话业务对象上下文（JSON 字符串，Java 组装）：供指代消解，仅内部理解不展示
    # 形如 [{"objectType":"contract","objectId":"1","title":"...","status":"signed"}]
    context_entities: str = Field(default="", max_length=8000, description="最近业务对象上下文（JSON 字符串，指代消解用）")
    # 早前对话摘要（Java 组装）：滑动窗口之外的老对话压成的一段话。
    # build_messages 把它插在 system 之后、最近对话之前，标注「[早前对话摘要]」，
    # 让模型知道这是压缩过的旧信息，而不是用户刚说的。
    history_summary: str = Field(default="", description="早前对话摘要（压缩，拼在最近对话之前）")


class CompactRequest(BaseModel):
    """对话压缩请求体（Java 侧上下文超阈值时异步调用）"""
    old_summary: str = Field(default="", description="已有摘要；空表示首次压缩")  # 增量：旧摘要 + 新消息 → 新摘要
    messages: list[ChatMessage] = Field(default_factory=list, description="待压缩的老消息（升序）")  # 被窗口挤出去的那批


class CompactResponse(BaseModel):
    """对话压缩结果"""
    summary: str = Field(default="", description="合并后的新摘要")  # 覆盖写回 ai_conversations.summary


# ==================== /images/describe — 图片描述接口 ====================

class DescribeImageRequest(BaseModel):
    """图片描述请求体（Java 侧首次使用历史图时调用）"""
    image_url: str = Field(..., description="图片可访问 URL（Java 现签的短效地址）")


class DescribeImageResponse(BaseModel):
    """图片描述结果"""
    description: str = Field(default="", description="单行描述；空串表示未识别出有效内容")


# ==================== /documents/parse — 文档解析接口 ====================

class WeatherAdviceRequest(BaseModel):
    """在途天气异常 → 处置建议"""
    place: str = Field(default="", description="异常地点")
    evidence: str = Field(default="", description="规则引擎证据")
    summary: str = Field(default="", description="短描述")
    level: str = Field(default="", description="medium / high")
    delivery_no: str = Field(default="", description="交付单号")
    ship_company: str = Field(default="", description="承运商")
    delivery_place: str = Field(default="", description="交货地")
    eta: str = Field(default="", description="预计到货")
    promised_date: str = Field(default="", description="合同/承诺交期")
    impact_qty: str = Field(default="", description="影响数量")


class WeatherAdviceResponse(BaseModel):
    """天气处置建议（每条一行）"""
    lines: list[str] = Field(default_factory=list, description="可执行建议")


class DocumentParseRequest(BaseModel):
    """文档解析请求体（Java 上传后调用）"""
    document_id: int = Field(..., description="文档ID（对应 ai_documents.id）")  # 文档主键
    tenant_id: int = Field(..., description="租户ID")  # 租户标识
    oss_url: str = Field(..., description="OSS 预签名下载地址")  # 原始文件地址
    file_name: str = Field(..., description="原始文件名")  # 文件名
    file_type: str = Field(..., description="文件类型：txt / md / pdf")  # 类型
