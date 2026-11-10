"""文档解析的统一中间表示与失败原因码。

所有格式的解析器最终都产出 ``chunker.Block``，Pipeline 再统一交给切分器。
失败一律抛 ``DocumentParseError``，由 API 层转成结构化 detail，
Java 侧据原因码给用户可读提示，而不是笼统的「解析失败」。
"""
from dataclasses import dataclass, field
from enum import Enum


class ParseErrorCode(str, Enum):
    """解析失败原因码（跨服务契约，改动需同步 Java ParseErrorSupport）。"""

    UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE"
    EMPTY_TEXT = "EMPTY_TEXT"
    SCANNED_NEEDS_OCR = "SCANNED_NEEDS_OCR"
    OCR_FAILED = "OCR_FAILED"
    OCR_PAGE_LIMIT = "OCR_PAGE_LIMIT"
    ENCRYPTED = "ENCRYPTED"
    PARSE_ERROR = "PARSE_ERROR"


class DocumentParseError(Exception):
    """带原因码的解析异常。"""

    def __init__(self, code: ParseErrorCode, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class DocMeta:
    """文档级解析元数据（用于日志、质量门禁与前端提示）。"""

    real_type: str = ""
    parser: str = ""
    page_count: int = 0
    parsed_pages: int = 0
    char_count: int = 0
    scanned_pages: int = 0
    ocr_pages: int = 0
    ocr_failed_pages: int = 0
    quality_score: float = 1.0
    warnings: list[str] = field(default_factory=list)

    @property
    def text_density(self) -> float:
        """每页有效字符数（无页码的格式如 docx 返回 0）。"""
        return self.char_count / self.parsed_pages if self.parsed_pages else 0.0


@dataclass
class ParseResult:
    """解析产物：结构块 + 元数据。"""

    blocks: list
    meta: DocMeta
