"""页眉页脚清洗测试（L3 清洗层）。

锁定三条约束：
  1. 跨页重复的页眉/页脚才删（≥3 页且 ≥30% 页面），两页文档不误删；
  2. 页码行（- 1 - / 第 2 页 共 3 页 / 3/3）无论是否重复都删；
  3. 只删页面首尾边际区域内的行，正文中间的重复内容必须保留。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.docparse.clean.header_footer import strip_running_lines


def test_repeated_header_removed_across_pages():
    pages = [
        "华东精密有限公司\n第一季度经营情况\n正文甲",
        "华东精密有限公司\n第二季度经营情况\n正文乙",
        "华东精密有限公司\n第三季度经营情况\n正文丙",
    ]
    cleaned, stats = strip_running_lines(pages)

    assert all("华东精密" not in p for p in cleaned), cleaned
    assert "第一季度经营情况" in cleaned[0], "正文首行被误删"
    assert stats.removed_lines == 3


def test_repeated_footer_removed():
    pages = [
        "正文甲\n内部资料 请勿外传",
        "正文乙\n内部资料 请勿外传",
        "正文丙\n内部资料 请勿外传",
    ]
    cleaned, _ = strip_running_lines(pages)
    assert all("内部资料" not in p for p in cleaned), cleaned


def test_page_number_lines_removed_regardless_of_repetition():
    pages = ["- 1 -\n正文甲", "第 2 页 共 3 页\n正文乙", "3/3\n正文丙"]
    cleaned, stats = strip_running_lines(pages)

    assert cleaned == ["正文甲", "正文乙", "正文丙"], cleaned
    assert stats.removed_lines == 3


def test_two_page_document_does_not_drop_repeated_line():
    """只有两页时重复可能只是巧合，阈值未达到，不得删除。"""
    pages = ["华东精密有限公司\n正文甲", "华东精密有限公司\n正文乙"]
    cleaned, _ = strip_running_lines(pages)
    assert "华东精密" in cleaned[0], cleaned


def test_repeated_body_line_outside_margins_kept():
    """正文中间重复的条款文字不在首尾边际区，必须保留。"""
    pages = [
        "\n".join(["页眉公司名", f"内容{i}-1", f"内容{i}-2", "3.2 付款方式：货到付款。",
                   f"内容{i}-3", f"内容{i}-4", f"内容{i}-5", f"内容{i}-6", "内部资料 请勿外传"])
        for i in range(3)
    ]
    cleaned, _ = strip_running_lines(pages)

    assert all("付款方式" in p for p in cleaned), cleaned
    assert all("页眉公司名" not in p and "内部资料" not in p for p in cleaned), cleaned


def test_empty_pages_pass_through():
    cleaned, stats = strip_running_lines(["", "\n", ""])
    assert [p.strip() for p in cleaned] == ["", "", ""]
    assert stats.removed_lines == 0


def test_roman_numeral_page_numbers_removed_in_margin():
    """目录页常见的罗马数字页码（V / VI / VII）也要在页面边际区删除。"""
    pages = ["V\n正文甲", "VI\n正文乙", "VII\n正文丙"]
    cleaned, _ = strip_running_lines(pages)
    assert [p.strip() for p in cleaned] == ["正文甲", "正文乙", "正文丙"], cleaned
