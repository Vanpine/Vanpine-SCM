"""
X-API-Key 鉴权中间件测试

覆盖 P2-1：
  (a) 正确 key 放行
  (b) 错误 key 拒绝 401
  (c) 缺 key 拒绝 401
  (d) /api/health 免鉴权放行
  (e) token 未配置（空）时一律拒绝（fail-closed）
"""
import asyncio
import sys
import types
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

# 本机可能未装 fastapi/starlette（生产环境有）；这里打桩，让 security.py 能 import
# 仅用于单测，不改变被测逻辑（被测的是 compare_digest 分支）。
for _mod, _attrs in (("fastapi", ("Request", "HTTPException")),
                     ("starlette.responses", ("JSONResponse",))):
    if _mod not in sys.modules:
        m = types.ModuleType(_mod)
        for a in _attrs:
            setattr(m, a, object)
        sys.modules[_mod] = m
# starlette.responses.JSONResponse 需要能构造并暴露 status_code
sys.modules["starlette.responses"].JSONResponse = (
    lambda status_code=200, content=None: type("R", (), {"status_code": status_code})()
)

from app import security  # noqa: E402


class _Req:
    """最小 Request 替身：只暴露 url.path / method / headers.get。"""

    def __init__(self, path="/api/chat", method="POST", key=None):
        self.url = type("U", (), {"path": path})()
        self.method = method
        self.headers = {} if key is None else {"X-API-Key": key}

    def headers_get(self, name, default=""):
        return self.headers.get(name, default)


def _run(req, token):
    """用指定 token 值跑一次中间件，返回 (是否放行, 状态码)。"""
    orig = security.AI_SERVICE_TOKEN
    security.AI_SERVICE_TOKEN = token
    passed = {"ok": False}

    async def _call_next(_):
        passed["ok"] = True
        return "NEXT"

    try:
        resp = asyncio.run(security.api_key_middleware(req, _call_next))
    finally:
        security.AI_SERVICE_TOKEN = orig
    if passed["ok"]:
        return True, 200
    status = getattr(resp, "status_code", 401)
    return False, status


def test_correct_key_passes():
    """(a) 正确 key 放行。"""
    ok, _ = _run(_Req(key="secret123"), "secret123")
    assert ok, "正确 key 应放行"


def test_wrong_key_rejected():
    """(b) 错误 key 拒绝。"""
    ok, code = _run(_Req(key="wrong"), "secret123")
    assert not ok and code == 401


def test_missing_key_rejected():
    """(c) 缺 key 拒绝。"""
    ok, code = _run(_Req(key=None), "secret123")
    assert not ok and code == 401


def test_health_exempt():
    """(d) /api/health 免鉴权。"""
    ok, _ = _run(_Req(path="/api/health", method="GET", key=None), "secret123")
    assert ok, "健康检查应放行"


def test_empty_token_fail_closed():
    """(e) 服务端 token 未配置时，即使客户端也不带 key 也必须拒绝。"""
    ok, code = _run(_Req(key=None), "")
    assert not ok and code == 401, "token 未配置必须 fail-closed"
    # 即使是空 key 也不能因为 compare_digest('','') 相等而放行
    ok2, _ = _run(_Req(key=""), "")
    assert not ok2, "空 key + 空 token 必须拒绝"


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
