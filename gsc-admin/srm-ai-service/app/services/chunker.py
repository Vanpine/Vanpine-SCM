"""
结构化切分器

策略：结构边界为主，递归字符兜底。
  ① 识别文档结构块（标题/条款/段落/列表/表格/目录）
  ② 章节、条款为硬边界，绝不跨边界合并
  ③ 结构块内超长时，才退化成「按段落 → 按句 → 硬切」的递归字符逻辑

中文合同/规格书天然带结构标记（第X条 / 一、 / 1.1 / # 标题），
这些标记就是语义边界，比任何字符分隔符都准；
纯递归字符切分不知道「第X条」是边界，会把相邻条款拼进同一块导致答错。
"""
import re
from dataclasses import dataclass, field

from app.services.tokenizer import count_tokens

# ==================== 切分参数 ====================

# 目标块 token 上限（装得下「一条条款 + 其款项」而不夹带下一条）
CHUNK_TARGET_TOKENS = 384
# 硬顶：块绝不超过此值，超出强制切
CHUNK_MAX_TOKENS = 512
# 软底：低于此值的章节尾块，仅「同章节内」并入上一块，绝不跨条款
CHUNK_MIN_TOKENS = 96
# 硬切时的尾部重叠（约 12%），仅块被 CHUNK_MAX 强切时加
TAIL_OVERLAP_TOKENS = 64
# 标题行最大长度（超过则不判为标题，防把长句误判成标题）
HEADING_MAX_CHARS = 40
# 连续短编号行达到此数量，判定为列表/规格参数行（非逐行标题）
LIST_RUN_THRESHOLD = 3

# ==================== 结构识别正则 ====================

# Markdown ATX 标题
_MD_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
# Markdown Setext 下划线标题（下一行 === 或 ---）
_SETEXT_RE = re.compile(r"^\s*(={2,}|-{2,})\s*$")
# 中文条款：第X条 / 第X章 / 第X节 / 第X款
_CLAUSE_RE = re.compile(r"^\s*第\s*[一二三四五六七八九十百零〇\d]+\s*[条章节款项]")
# 数字大纲：1. / 1.1 / 1.1.1（须后随正文，防日期误判）
_NUMBER_HEADING_RE = re.compile(r"^\s*(\d+(?:\.\d+){0,4})\s*[、.．)\s]\s*\S")
# 纯数字行（规格书参数行：`1 输入电压 AC220V`）
_NUM_LINE_RE = re.compile(r"^\s*\d+(?:\.\d+)*\s+\S")
# 中文顿号/罗马数字小节：一、二、三、
_CN_SECTION_RE = re.compile(r"^\s*[一二三四五六七八九十]+[、.．]\s*\S")
# 目录行尾：点线 + 页码（覆盖 ASCII 点/中文省略号/间隔号 + 空格点线）
_TOC_TAIL_RE = re.compile(r"[.·…]{2,}\s*\d+$|\s{2,}\d+$")
# 日期伪标题（2023.12.31 类）的排除：行尾是数字即疑似日期
_DATE_LIKE_RE = re.compile(r"^\s*\d{4}[.\-/]\d{1,2}[.\-/]\d{1,2}\b")


@dataclass
class Block:
    """文档结构块"""

    text: str
    kind: str  # heading | clause | para | list | table | toc
    level: int = 0  # 标题层级（0=非标题）
    heading_path: list[str] = field(default_factory=list)  # 章节路径
    page: int | None = None  # 页码（pdf 用，txt/md 为 None）


# ==================== 结构块抽取 ====================


def extract_blocks(text: str) -> list[Block]:
    """把纯文本解析为结构块列表。

    维护标题栈计算 heading_path；连续目录行整段合并为 kind=toc（不入库）。

    Args:
        text: 已抽取的纯文本

    Returns:
        Block 列表（toc 块需调用方在入库前过滤）
    """
    if not text or not text.strip():
        return []

    lines = text.split("\n")
    blocks: list[Block] = []
    heading_stack: list[str] = []  # 标题栈：[0]=一级标题…
    buffer: list[str] = []  # 待聚合的正文段落
    toc_buffer: list[str] = []  # 待聚合的目录行
    num_run: list[str] = []  # 连续短编号行（用于判列表）

    def flush_para() -> None:
        """把当前正文缓冲收成一个 para 块"""
        if not buffer:
            return
        content = "\n".join(buffer).strip()
        buffer.clear()
        if content:
            blocks.append(Block(text=content, kind="para", heading_path=list(heading_stack)))

    def flush_num_run() -> None:
        """连续短编号行收成一个 list 块；未达 LIST_RUN_THRESHOLD 回落为 para。

        孤立编号行（如正文里偶然出现的一行 `1 输入电压 AC220V`）不具备列表语义，
        标成 list 会让 chunk_type 失真；只有连续出现才判为规格参数列表。
        """
        if not num_run:
            return
        content = "\n".join(num_run).strip()
        kind = "list" if len(num_run) >= LIST_RUN_THRESHOLD else "para"
        num_run.clear()
        if content:
            blocks.append(Block(text=content, kind=kind, heading_path=list(heading_stack)))

    def flush_toc() -> None:
        """连续目录行收成一个 toc 块"""
        if not toc_buffer:
            return
        content = "\n".join(toc_buffer).strip()
        toc_buffer.clear()
        if content:
            blocks.append(Block(text=content, kind="toc", heading_path=list(heading_stack)))

    for i, raw_line in enumerate(lines):
        line = raw_line.rstrip()
        stripped = line.strip()

        # 空行：段落边界，收尾正文/编号行/目录
        if not stripped:
            flush_para()
            flush_num_run()
            flush_toc()
            continue

        # 目录行：与正文条线/编号前缀 + 页码尾 → 归 toc，整段合并
        if _TOC_TAIL_RE.search(stripped) and (
            _CLAUSE_RE.match(stripped)
            or _NUMBER_HEADING_RE.match(stripped)
            or _CN_SECTION_RE.match(stripped)
        ):
            flush_para()
            flush_num_run()
            toc_buffer.append(stripped)
            continue
        # 非目录行 → 若目录缓冲中，先收尾
        flush_toc()

        # Markdown ATX 标题
        md = _MD_HEADING_RE.match(stripped)
        if md:
            flush_para()
            flush_num_run()
            level = len(md.group(1))
            title = md.group(2).strip()
            _push_heading(heading_stack, level, title)
            blocks.append(Block(text=title, kind="heading", level=level,
                                heading_path=list(heading_stack)))
            continue

        # Setext 标题（下一行 === 或 ---）：当前行是标题文本
        if i + 1 < len(lines) and _SETEXT_RE.match(lines[i + 1].strip()) and len(stripped) <= HEADING_MAX_CHARS:
            flush_para()
            flush_num_run()
            level = 1 if lines[i + 1].strip().startswith("=") else 2
            _push_heading(heading_stack, level, stripped)
            blocks.append(Block(text=stripped, kind="heading", level=level,
                                heading_path=list(heading_stack)))
            continue

        # 中文条款：硬边界
        if _CLAUSE_RE.match(stripped) and len(stripped) <= HEADING_MAX_CHARS:
            flush_para()
            flush_num_run()
            blocks.append(Block(text=stripped, kind="clause", level=1,
                                heading_path=list(heading_stack)))
            continue

        # 连续短编号行（规格参数行）→ 暂存，凑够阈值判 list。
        # 须在数字大纲标题之前判断：`1 输入电压 AC220V` 既像编号行又像大纲，
        # 靠「连续出现」区分——连续编号行是列表，孤立编号行才可能是章节标题。
        if _is_list_line(stripped):
            flush_para()
            num_run.append(stripped)
            continue
        if num_run:
            flush_num_run()

        # 数字大纲标题（须非日期伪命中，且行长受控）
        if (_NUMBER_HEADING_RE.match(stripped) and not _DATE_LIKE_RE.match(stripped)
                and len(stripped) <= HEADING_MAX_CHARS):
            flush_para()
            level = stripped.count(".") + 1
            _push_heading(heading_stack, level, stripped)
            blocks.append(Block(text=stripped, kind="heading", level=level,
                                heading_path=list(heading_stack)))
            continue

        # 普通正文行 → 累积到段落缓冲
        buffer.append(stripped)

    flush_para()
    flush_num_run()
    flush_toc()
    return blocks


def _push_heading(stack: list[str], level: int, title: str) -> None:
    """把标题压入标题栈（同/更高层级先弹出），更新 heading_path。"""
    while len(stack) >= level:
        stack.pop()
    while len(stack) < level - 1:  # 补齐跳级（如 # → ###）
        stack.append("")
    stack.append(title)


def _is_list_line(line: str) -> bool:
    """判断是否为列表/规格参数行（短编号行，非章节标题）。

    区分「列表行」与「数字大纲标题」：
      - 列表行：`1 输入电压 AC220V` — 编号后直接跟内容，无点号分隔，且短
      - 大纲标题：`1.1 付款条款` — 编号带点号，通常是章节名
    这里把「纯数字+空格开头」「中文顿号小节」都归为潜在列表行；
    连续出现（≥LIST_RUN_THRESHOLD）时由调用方聚合为 list 块。
    """
    if len(line) > HEADING_MAX_CHARS:
        return False
    if _CN_SECTION_RE.match(line):
        return True
    # 纯数字行：`1 xxx`（无点号）或 `1.1 xxx` 但内容短且非标题式
    return bool(_NUM_LINE_RE.match(line))


# ==================== 按结构边界切分 ====================


def build_chunks(blocks: list[Block]) -> list[dict]:
    """把结构块切成最终分块。

    章节/条款为硬边界，绝不跨边界合并；结构块内超长时递归字符兜底。

    Args:
        blocks: extract_blocks 的输出

    Returns:
        [{"content", "heading_path", "chunk_type", "token_count", "page", "level"}]，toc 块已过滤
    """
    chunks: list[dict] = []
    current: list[str] = []  # 当前聚合的正文字段
    current_tokens = 0
    current_path: list[str] = []
    current_kind = "para"
    current_page: int | None = None  # 当前块起始页（pdf 用）
    current_level = 0  # 当前块所属标题层级（非标题为 0）

    def flush() -> None:
        nonlocal current, current_tokens, current_kind, current_page, current_level
        if not current:
            return
        content = "\n".join(current).strip()
        if content:
            chunks.append({
                "content": content,
                "heading_path": list(current_path),
                "chunk_type": current_kind,
                "token_count": count_tokens(content),
                # page/level 透传：引用需标页码、层级需还原面包屑，不能在此丢弃
                "page": current_page,
                "level": current_level,
            })
        current = []
        current_tokens = 0
        current_page = None
        current_level = 0

    for b in blocks:
        # 目录不进库
        if b.kind == "toc":
            continue

        # 硬边界：标题 / 条款 → 先收尾，边界本身不单独成块（并入其正文）
        if b.kind in ("heading", "clause"):
            flush()
            current_path = list(b.heading_path)
            current_kind = "clause" if b.kind == "clause" else "para"
            current = [b.text]
            current_tokens = count_tokens(b.text)
            current_page = b.page
            current_level = b.level
            continue

        block_tokens = count_tokens(b.text)

        # 原子块（表格）：独立成块，不与前后合并；超硬顶时按行切并重复表头
        if b.kind == "table":
            flush()
            for piece in _split_table(b.text):
                chunks.append({
                    "content": piece,
                    "heading_path": list(b.heading_path),
                    "chunk_type": "table",
                    "token_count": count_tokens(piece),
                    "page": b.page,
                    "level": b.level,
                })
            continue

        # 结构块内超长：递归字符兜底
        if block_tokens > CHUNK_MAX_TOKENS:
            flush()
            for piece in _split_recursive(b.text):
                chunks.append({
                    "content": piece,
                    "heading_path": list(b.heading_path),
                    "chunk_type": "para",
                    "token_count": count_tokens(piece),
                    "page": b.page,
                    "level": b.level,
                })
            continue

        # 同章节内贪心聚合
        if current_path != list(b.heading_path) or current_tokens + block_tokens > CHUNK_TARGET_TOKENS:
            flush()
        if not current:
            current_path = list(b.heading_path)
            current_kind = b.kind
            current_page = b.page
            current_level = b.level
        current.append(b.text)
        current_tokens += block_tokens

    flush()

    # 尾块合并：仅同 heading_path 且低于软底时并入上一块
    chunks = _merge_short_tail(chunks)
    # 丢弃「仅标题、无正文」的空碎片块（闭卷标题行不成独立向量点）
    chunks = _drop_empty_fragments(chunks, blocks)
    return chunks


def _drop_empty_fragments(chunks: list[dict], blocks: list[Block]) -> list[dict]:
    """过滤纯标题碎片：标题无正文（块内容==标题本身）且非表格时丢弃。

    闭卷标题（如文档大标题、未被正文跟进的目录标题）独立成块只会变成噪声向量点。
    """
    heading_texts = {b.text.strip() for b in blocks if b.kind == "heading"}
    kept: list[dict] = []
    for c in chunks:
        content = c["content"].strip()
        if c["chunk_type"] != "table" and content in heading_texts:
            continue  # 块内容就是标题本身 → 无正文，丢弃
        kept.append(c)
    return kept


def _merge_short_tail(chunks: list[dict]) -> list[dict]:
    """同章节内、低于 CHUNK_MIN_TOKENS 的尾块并入上一块（绝不跨章节）。"""
    if len(chunks) < 2:
        return chunks
    for i in range(len(chunks) - 1, 0, -1):
        cur = chunks[i]
        prev = chunks[i - 1]
        if (cur["chunk_type"] != "table" and prev["chunk_type"] != "table"
                and cur["heading_path"] == prev["heading_path"]
                and count_tokens(cur["content"]) < CHUNK_MIN_TOKENS):
            merged = prev["content"] + "\n" + cur["content"]
            prev["content"] = merged
            prev["token_count"] = count_tokens(merged)
            chunks.pop(i)
    return chunks


# ==================== 递归字符兜底 ====================

# 分隔符优先级：空行 → 换行 → 中文句末 → 中文逗号 → 硬切
_SEPARATORS = ["\n\n", "\n", "。", "！", "？", "；", "，", " "]


def _split_table(text: str) -> list[str]:
    """表格块超硬顶时按数据行切分，每片重复 Markdown 表头。

    表格是解析器按固定行数（TABLE_MAX_ROWS）产出的，行数固定但单元格文本长时
    单块 token 可远超 CHUNK_MAX——若不切，embedder 会静默截断丢掉后半张表。
    这里按 token 预算逐行累加封片，每片带上表头（前两行：表头 + 分隔线），
    保证每片独立可读、可被检索命中。

    Args:
        text: Markdown 管道表全文

    Returns:
        切分后的表格片列表；未超限时原样返回单元素列表
    """
    if count_tokens(text) <= CHUNK_MAX_TOKENS:
        return [text] if text.strip() else []

    lines = text.split("\n")
    # Markdown 管道表：第 0 行表头、第 1 行分隔线（|---|），其余为数据行
    if len(lines) < 3 or not _is_table_sep(lines[1]):
        # 非标准表格结构，退回递归字符切分，防按行切错
        return _split_recursive(text)

    header = lines[:2]
    header_tokens = count_tokens("\n".join(header))
    pieces: list[str] = []
    buf: list[str] = list(header)
    buf_tokens = header_tokens
    for row in lines[2:]:
        row_tokens = count_tokens(row) + 1  # +1 补换行
        # 加本行会超限且已有数据行 → 先封当前片（表头 + 已累计行），再开新片重复表头
        if buf_tokens + row_tokens > CHUNK_MAX_TOKENS and len(buf) > len(header):
            pieces.append("\n".join(buf))
            buf = list(header)
            buf_tokens = header_tokens
        buf.append(row)
        buf_tokens += row_tokens
    if len(buf) > len(header):
        pieces.append("\n".join(buf))
    return pieces


def _is_table_sep(line: str) -> bool:
    """判断是否为 Markdown 表格分隔线（|---|:--:| 类）。"""
    s = line.strip()
    if not s.startswith("|") or "-" not in s:
        return False
    return set(s) <= set("|-: ")


def _split_recursive(text: str) -> list[str]:
    """结构块内超长文本的递归字符切分（对齐主流 RecursiveCharacterTextSplitter 思路）。

    仅用于「结构块本身超过 CHUNK_MAX_TOKENS」的兜底场景。
    块被强切时，下一块带上一块尾部的整句重叠（TAIL_OVERLAP_TOKENS）。
    """
    if count_tokens(text) <= CHUNK_MAX_TOKENS:
        return [text] if text.strip() else []

    units = _split_by_separators(text)
    pieces: list[str] = []
    current = ""
    for unit in units:
        candidate = unit if not current else current + unit
        if current and count_tokens(candidate) > CHUNK_MAX_TOKENS:
            pieces.append(current)
            # 尾部整句重叠
            current = _tail_overlap(current) + unit
        else:
            current = candidate
    if current.strip():
        pieces.append(current)

    # 兜底：无任何分隔符的极长串，按字符硬切
    out: list[str] = []
    for p in pieces:
        while count_tokens(p) > CHUNK_MAX_TOKENS:
            cut = _hard_cut_index(p, CHUNK_MAX_TOKENS)
            out.append(p[:cut])
            p = p[cut:]
        if p.strip():
            out.append(p)
    return out


def _split_by_separators(text: str, seps: list[str] | None = None) -> list[str]:
    """按分隔符优先级递归切分，保留分隔符。

    真递归：先用最高优先级分隔符切，对每个仍超 CHUNK_TARGET 的片段用「剩余分隔符」
    继续切，直到所有分隔符用尽（调用方再走字符硬切兜底）。
    旧实现只挑第一个命中的分隔符切一次，子片段超长就直接落到字符硬切 → 句子被劈开。

    Args:
        text: 待切文本
        seps: 可用分隔符（按优先级）；None 时取全量 _SEPARATORS

    Returns:
        语义单元列表（每段末尾保留其分隔符），拼接可还原原文
    """
    if count_tokens(text) <= CHUNK_TARGET_TOKENS:
        return [text]
    if seps is None:
        seps = _SEPARATORS
    for i, sep in enumerate(seps):
        if sep in text:
            parts = text.split(sep)
            # 分隔符拼回每段末尾（除最后一段），保持可还原
            merged = [p + sep for p in parts[:-1]] + [parts[-1]]
            # 对仍超限的片段用后续分隔符继续递归；已够小的原样保留
            out: list[str] = []
            for piece in merged:
                if count_tokens(piece) > CHUNK_TARGET_TOKENS:
                    out.extend(_split_by_separators(piece, seps[i + 1:]))
                else:
                    out.append(piece)
            return out
    # 无任何分隔符可用：原样返回，交调用方字符硬切
    return [text]


def _tail_overlap(text: str) -> str:
    """从块尾取整句，累计不超过 TAIL_OVERLAP_TOKENS。"""
    sentences = re.split(r"(?<=[。！？!?；;])|\n", text)
    tail: list[str] = []
    tokens = 0
    for s in reversed(sentences):
        s = s.strip()
        if not s:
            continue
        t = count_tokens(s)
        if tokens + t > TAIL_OVERLAP_TOKENS:
            break
        tail.insert(0, s)
        tokens += t
    return "".join(tail)


def _hard_cut_index(text: str, max_tokens: int) -> int:
    """二分找不超过 max_tokens 的最大切点。"""
    lo, hi = 1, len(text)
    while lo < hi:
        mid = (lo + hi + 1) // 2
        if count_tokens(text[:mid]) <= max_tokens:
            lo = mid
        else:
            hi = mid - 1
    return lo


# ==================== parent-child 两层 ====================

# 父块目标 token（回填给 LLM 的上下文单元，按章节聚合）
PARENT_TARGET_TOKENS = 1200


def build_parent_child(blocks: list[Block]) -> tuple[list[dict], list[dict]]:
    """在结构化切分之上构建父子两层。

    父块 = 同一 heading_path 的连续子块聚合（约 PARENT_TARGET_TOKENS），只回填不进向量；
    子块 = build_chunks 产出的小块，进向量用于检索命中。

    Args:
        blocks: extract_blocks 的输出

    Returns:
        (children, parents)
        children: build_chunks 结果，每项追加 parent_id + parent_heading_path
        parents:  [{"parent_id", "content", "heading_path", "token_count"}]
    """
    children = build_chunks(blocks)
    if not children:
        return [], []

    parents: list[dict] = []
    # 按 (heading_path, 父块序号) 聚父块：同章节内累计到 PARENT_TARGET_TOKENS 即封一个父块
    cur_pid: str | None = None
    cur_texts: list[str] = []
    cur_tokens = 0
    cur_path: list[str] = []
    seq = 0

    def flush_parent() -> None:
        nonlocal cur_pid, cur_texts, cur_tokens, seq
        if cur_pid is None or not cur_texts:
            return
        content = "\n".join(cur_texts).strip()
        if content:
            parents.append({
                "parent_id": cur_pid,
                "content": content,
                "heading_path": list(cur_path),
                "token_count": count_tokens(content),
            })
        cur_pid = None
        cur_texts = []
        cur_tokens = 0

    for c in children:
        path = c.get("heading_path") or []
        tok = c.get("token_count", 0)
        # 换章节 或 超父块上限 → 封当前父块
        new_path = path != cur_path
        over = cur_pid is not None and cur_tokens + tok > PARENT_TARGET_TOKENS
        if new_path or over:
            flush_parent()
        if cur_pid is None:
            seq += 1
            # 用 heading_path + 序号生成确定性 parent_id（调用方加 document_id 前缀保证唯一）
            cur_pid = f"p{seq}-{'/'.join(path) if path else 'root'}"
            cur_path = list(path)
        c["parent_id"] = cur_pid
        c["parent_heading_path"] = list(cur_path)
        cur_texts.append(c["content"])
        cur_tokens += tok

    flush_parent()
    return children, parents
