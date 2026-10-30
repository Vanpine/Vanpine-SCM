"""
工具结果裁剪测试

工具结果是上下文里唯一没有预算约束的部分：不裁的话 10 条合同的完整 DTO
（含 tenantId / buyerTenantId 等模型用不上的字段）就上千 token，
Agent 循环 4 轮还会把历轮结果一直背着。

覆盖 project_tool_result 的三条规则：
  (a) 列表类按白名单投影，裁掉 tenantId 等内部字段
  (b) 超过 10 条只给前 10 条 + 总数提示
  (c) 失败与超大结果有兜底，不会原样灌进去
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.tool_context import (  # noqa: E402
    MAX_ITEMS_IN_CONTEXT,
    MAX_TOOL_RESULT_CHARS,
    project_tool_result,
)

_CONTRACT = {
    "id": 2100434056083521538,
    "contractNo": "HT20261001",
    "title": "钢材年度框架采购合同",
    "amount": 320000.00,
    "currency": "CNY",
    "status": "signed",
    "statusText": "已签署",
    "signDate": "2026-09-15",
    "deliveryDate": "2026-11-05",
    "buyerTenantId": 2100000000000000001,
    "supplierTenantId": 2100000000000000002,
    "createTime": "2026-09-01T10:23:11",
}


def test_list_projection_drops_internal_fields():
    """(a) 列表类只留白名单字段。"""
    out = json.loads(project_tool_result("list_contracts", {"success": True, "result": [_CONTRACT]}))
    item = out["items"][0]
    assert item["contractNo"] == "HT20261001"
    assert item["statusText"] == "已签署"
    assert "buyerTenantId" not in item
    assert "supplierTenantId" not in item
    assert "createTime" not in item
    assert "status" not in item, "英文状态码不进上下文，中文 statusText 已够"


def test_list_total_hint_when_overflow():
    """(b) 超 10 条：只给前 10 条 + total/note。"""
    rows = [dict(_CONTRACT, id=i) for i in range(30)]
    out = json.loads(project_tool_result("list_contracts", {"success": True, "result": rows}))
    assert len(out["items"]) == MAX_ITEMS_IN_CONTEXT
    assert out["total"] == 30
    assert "仅展示前" in out["note"]


def test_failed_result_keeps_only_error():
    """失败只留错误语义，不把整个响应体灌进去。"""
    result = {"success": False, "result": None, "error": "供应商接口超时", "entities": [{"title": "x"}]}
    out = json.loads(project_tool_result("list_contracts", result))
    assert out == {"success": False, "error": "供应商接口超时"}


def test_detail_tool_not_projected():
    """详情类工具不裁字段（用户明确问的那一条要完整）。"""
    detail = {"success": True, "result": {"deliveryNo": "DL001", "traces": [{"node": "已发货"}],
                                          "logisticsEvents": [{"event": "到达杭州"}]}}
    out = json.loads(project_tool_result("get_delivery_detail", detail))
    assert out["result"]["traces"] == [{"node": "已发货"}]


def test_hard_cap_on_huge_result():
    """超大结果必须被字符上限截断并带说明。"""
    huge = {"success": True, "result": {"text": "字" * 20000}}
    text = project_tool_result("get_supplier_detail", huge)
    assert len(text) <= MAX_TOOL_RESULT_CHARS + 40
    assert "结果过长已截断" in text


def test_unknown_tool_does_not_crash():
    """未知工具走详情逻辑，不影响主流程。"""
    out = json.loads(project_tool_result("some_new_tool", {"success": True, "result": {"a": 1}}))
    assert out["result"] == {"a": 1}


if __name__ == "__main__":
    passed = failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                passed += 1
                print(f"  PASS {name}")
            except AssertionError as e:
                failed += 1
                print(f"  FAIL {name}: {e}")
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
