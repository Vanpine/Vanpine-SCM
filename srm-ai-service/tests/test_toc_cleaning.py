"""PDF 目录页清洗测试。

锁定：目录页（≥3 行「标题 + 点线 + 页码」且占比 ≥50%）整页丢弃，
避免目录被当成正文切进知识库；正文页偶有一行类似目录的引用不误删。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.docparse.clean.toc import is_toc_line, strip_toc_pages


def test_toc_page_dropped_when_majority_lines_are_toc():
    page = "\n".join([
        "目 录",
        "第 6 章 系统测试.........................................................................................60",
        "6.1 测试环境...............................................................................................61",
        "6.2 测试结果...............................................................................................64",
        "参考文献.....................................................................................................69",
    ])
    cleaned, stats = strip_toc_pages([page])

    assert cleaned == [""], cleaned
    assert stats.removed_pages == 1
    assert stats.removed_lines >= 4


def test_multi_page_toc_all_dropped():
    pages = [
        "目 录\n第一章 总则.........1\n第二章 采购.........2\n第三章 交付.........3",
        "第四章 结算.........4\n第五章 违约.........5\n附录 A 清单.........6\n附录 B 模板.........7",
    ]
    cleaned, stats = strip_toc_pages(pages)
    assert cleaned == ["", ""], cleaned
    assert stats.removed_pages == 2


def test_normal_page_with_single_reference_kept():
    page = "\n".join([
        "第 6 章 系统测试",
        "本章介绍测试环境、测试用例与测试结论。",
        "详见附录 A 测试清单.........123",
    ])
    cleaned, stats = strip_toc_pages([page])
    assert "本章介绍测试环境" in cleaned[0], cleaned
    assert stats.removed_pages == 0


def test_space_leader_toc_lines_detected():
    assert is_toc_line("第一章 总则        1")
    assert is_toc_line("参考文献        69")
    assert is_toc_line("附录 A 接口清单    120")


def test_normal_sentences_not_toc():
    assert not is_toc_line("本章介绍测试环境、测试用例与测试结论。")
    assert not is_toc_line("2026 年 3 月完成验收。")
    assert not is_toc_line("详见第 6 章。")
