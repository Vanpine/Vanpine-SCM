"""真实文档类型识别。

扩展名可以改、可以错，魔数不会说谎：
  1. 先看文件头（%PDF / PK / OLE / HTML）；
  2. OOXML（docx/xlsx/pptx）都是 zip，需看内部目录名区分；
  3. 旧版 doc/xls/ppt 同为 OLE 复合文档，只能结合扩展名；
  4. 最后才回退扩展名 / 文本启发式。
"""
import io
import zipfile

# OOXML 容器里的特征目录
_OOXML_ENTRIES = (
    ("word/document.xml", "docx"),
    ("xl/workbook.xml", "xlsx"),
    ("ppt/presentation.xml", "pptx"),
)
# 旧版 Office 的扩展名（OLE 魔数相同，靠扩展名区分）
_LEGACY_EXTENSIONS = {"doc", "xls", "ppt"}
# 纯文本类扩展名
_TEXT_EXTENSIONS = {"txt", "md", "markdown", "csv", "json", "log", "text"}


def detect_real_type(raw_bytes: bytes, file_type: str = "") -> str:
    """按内容优先、扩展名兜底的顺序识别真实类型。

    Args:
        raw_bytes: 文件字节
        file_type: 调用方给出的扩展名（可能为空或不准确）

    Returns:
        真实类型：pdf / docx / xlsx / pptx / doc / xls / ppt / ole /
        html / txt / md / csv / json / log / text / binary / zip
    """
    head = (raw_bytes or b"")[:8]
    ext = _normalize_ext(file_type)

    # 1. 魔数优先
    if head.startswith(b"%PDF"):
        return "pdf"
    if head.startswith(b"PK\x03\x04"):
        return _zip_kind(raw_bytes)
    if head.startswith(b"\xd0\xcf\x11\xe0"):
        return ext if ext in _LEGACY_EXTENSIONS else "ole"

    # 2. HTML（部分 HTML 文件能通过 UTF-8 解码，必须在文本回退前判定）
    probe = (raw_bytes or b"")[:64].lstrip().lower()
    if probe.startswith(b"<!doctype html") or probe.startswith(b"<html"):
        return "html"

    # 3. 扩展名
    if ext in _TEXT_EXTENSIONS:
        return "md" if ext == "markdown" else ext

    # 4. 文本启发式兜底
    return "txt" if _looks_text(raw_bytes) else "binary"


def _zip_kind(raw_bytes: bytes) -> str:
    """检查 zip 容器内部特征文件，区分 docx/xlsx/pptx 与普通 zip。"""
    try:
        with zipfile.ZipFile(io.BytesIO(raw_bytes)) as z:
            names = set(z.namelist())
    except Exception:
        return "zip"
    for entry, kind in _OOXML_ENTRIES:
        if entry in names:
            return kind
    return "zip"


def _looks_text(raw_bytes: bytes) -> bool:
    """用「能否按常见中文编码解码」粗判纯文本。"""
    if not raw_bytes:
        return True
    sample = raw_bytes[:4096]
    for encoding in ("utf-8", "gbk"):
        try:
            sample.decode(encoding)
            return True
        except (UnicodeDecodeError, LookupError):
            continue
    return False


def _normalize_ext(file_type: str) -> str:
    """扩展名归一：兼容传文件名的情况，取最后一个点之后、转小写。"""
    value = (file_type or "").strip().lower()
    if "." in value:
        value = value.rsplit(".", 1)[-1]
    return value.lstrip(".")
