"""
java_client 父块回查测试

覆盖 P7 验收点：
  (a) 无 tool_token / 无 keys 时不发请求，直接返回空（调用方回退子块原文）
  (b) 网络异常时返回空 dict（不抛异常，问答不中断）
  (c) 正常响应解析成 {parent_key: {content, heading_path, token_count}}

注：HTTP 层已从标准库 urllib 换成 httpx，打桩目标随之改为 java_client._client。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services import java_client


class _FakeResp:
    """最小 httpx.Response 替身：只实现 fetch_parents 用到的两个方法。"""

    def __init__(self, body):
        self._body = body

    def raise_for_status(self):
        pass

    def json(self):
        return self._body


def _with_client(fn, *, body=None, exc=None):
    """临时替换 java_client._client，跑 fn 后还原（避免污染其它测试）。"""

    class _FakeClient:
        def post(self, *a, **k):
            if exc is not None:
                raise exc
            return _FakeResp(body)

    original = java_client._client
    java_client._client = _FakeClient()
    try:
        return fn()
    finally:
        java_client._client = original


def test_no_token_returns_empty():
    """(a) 无令牌不发请求。"""
    assert java_client.fetch_parents(["k1"], "") == {}
    assert java_client.fetch_parents([], "tok") == {}


def test_network_error_returns_empty():
    """(b) 网络异常返回空 dict，不抛。"""
    assert _with_client(
        lambda: java_client.fetch_parents(["k1"], "tok"),
        exc=OSError("connection refused"),
    ) == {}


def test_parse_success():
    """(c) 正常响应解析正确。"""
    out = _with_client(
        lambda: java_client.fetch_parents(["pk1", "pk2"], "tok"),
        body={
            "code": 200,
            "data": [
                {"parentKey": "pk1", "content": "父文1", "headingPath": ["合同", "第三条"], "tokenCount": 120},
                {"parentKey": "pk2", "content": "父文2", "headingPath": [], "tokenCount": 80},
            ],
        },
    )
    assert out["pk1"]["content"] == "父文1"
    assert out["pk1"]["heading_path"] == ["合同", "第三条"]
    assert out["pk2"]["token_count"] == 80


def test_malformed_response_returns_empty():
    """(c) 响应无 data 时返回空。"""
    assert _with_client(
        lambda: java_client.fetch_parents(["k1"], "tok"),
        body={"code": 500, "message": "err"},
    ) == {}


if __name__ == "__main__":
    import traceback
    passed = failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print(f"[PASS] {name}")
                passed += 1
            except Exception as e:
                print(f"[FAIL] {name}: {e}")
                traceback.print_exc()
                failed += 1
    print(f"\n{passed} passed, {failed} failed")
    sys.exit(1 if failed else 0)
