"""解析质量门禁。

目标：让「解析成功但内容不可用」的文档显式失败，而不是静默入库空块/乱码。
  - 扫描件（多数页面没有文本层）→ SCANNED_NEEDS_OCR
  - 抽不出任何结构块 → EMPTY_TEXT
  - 部分扫描 / 文本密度低 → 进入 warnings，仍允许入库
"""
from app.docparse.types import DocumentParseError, DocMeta, ParseErrorCode

# 单页文本层字符数低于此值且页面含图片 → 判为扫描页（与主流实践一致：50 字符/页）
SCANNED_PAGE_CHAR_THRESHOLD = 50
# 扫描页占比达到此值 → 整份文档判为扫描件
SCANNED_PAGE_RATIO = 0.8
# 每页有效字符低于此值 → 文本密度告警（可能是稀疏版式/大量图片）
LOW_DENSITY_PER_PAGE = 200
# 质量分归一用的「健康密度」
HEALTHY_DENSITY_PER_PAGE = 300


def evaluate(blocks: list, meta: DocMeta) -> None:
    """执行质量门禁；不通过时抛 DocumentParseError，通过时回填 meta。

    Args:
        blocks: 解析出的结构块
        meta: 文档元数据（由 Pipeline 组装的页面统计）

    Raises:
        DocumentParseError: 扫描件 / 空文档
    """
    # 1. PDF 先判扫描件：全篇没有可用文本层时，任何切分都没有意义
    if meta.real_type == "pdf":
        _check_pdf_scanned(meta)

    # 2. 无任何结构块 → 空文档（扫描件已在上面给出更精确的原因码）
    if not blocks:
        raise DocumentParseError(
            ParseErrorCode.EMPTY_TEXT,
            "文档中未提取到有效文本，请检查文件内容或格式后重试",
        )

    # 3. 文本密度告警（不阻断）
    if meta.real_type == "pdf" and meta.text_density < LOW_DENSITY_PER_PAGE:
        meta.warnings.append(
            f"文本密度偏低（{meta.text_density:.0f} 字符/页），解析质量可能受影响"
        )

    # 4. 综合质量分（观测用，不参与门禁判定）
    meta.quality_score = _score(meta)


def _check_pdf_scanned(meta: DocMeta) -> None:
    """按扫描页占比判定整份 PDF 是否属于扫描件（区分「未 OCR」与「OCR 失败」）。"""
    parsed = meta.parsed_pages or meta.page_count
    if parsed <= 0:
        return

    failed = meta.ocr_failed_pages
    ocr_attempted = bool(meta.ocr_pages or failed)

    # OCR 已尝试但失败比例过高 → 给 OCR 失败原因码，而不是笼统的扫描件
    if ocr_attempted and failed / parsed >= SCANNED_PAGE_RATIO:
        raise DocumentParseError(
            ParseErrorCode.OCR_FAILED,
            f"文档为扫描件，OCR 失败 {failed}/{parsed} 页，"
            "请稍后重试或上传可复制文本的 PDF",
        )

    # 未做 OCR 且扫描比例过高 → 原有扫描件判定
    if not ocr_attempted and meta.scanned_pages / parsed >= SCANNED_PAGE_RATIO:
        raise DocumentParseError(
            ParseErrorCode.SCANNED_NEEDS_OCR,
            f"文档疑似扫描件（{meta.scanned_pages}/{parsed} 页无可提取文本），"
            "请上传 OCR 版本或可复制文本的 PDF",
        )

    # 部分扫描且未启用 OCR：保留可解析部分并告警（OCR 已在解析器侧记录通过/失败页数）
    if not ocr_attempted and meta.scanned_pages:
        meta.warnings.append(
            f"检测到 {meta.scanned_pages}/{parsed} 页疑似扫描页，已跳过无文本页"
        )


def _score(meta: DocMeta) -> float:
    """计算 0~1 的质量分：文本密度 + 可用页占比（OCR 后按失败页计算）。"""
    if meta.real_type != "pdf":
        return 1.0

    density_score = min(1.0, meta.text_density / HEALTHY_DENSITY_PER_PAGE)
    parsed = meta.parsed_pages or 0
    # OCR 尝试过 → 看 OCR 失败页；未尝试 → 看扫描页
    bad_pages = (meta.ocr_failed_pages
                 if (meta.ocr_pages or meta.ocr_failed_pages)
                 else meta.scanned_pages)
    page_score = 1.0 - (bad_pages / parsed) if parsed else 1.0
    return round(0.6 * density_score + 0.4 * page_score, 2)
