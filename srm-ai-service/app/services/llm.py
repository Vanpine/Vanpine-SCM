"""
DeepSeek LLM 客户端构造 — 对话链路与工具链路的客户端工厂。

抽出来的原因：agent 循环与对话压缩都要用，留在 chat.py 会让它继续变重。
两个客户端都是**模块级单例**：ChatOpenAI 内部持有 httpx 连接池，
每次调用新建会丢掉 keep-alive，高并发下表现为每轮对话都重新 TCP/TLS 握手。
"""
import threading  # 单例延迟创建时的互斥（reasoning 侧会从线程池并发调用）

from langchain_openai import ChatOpenAI  # DeepSeek 兼容 OpenAI API

from app.services.reasoning import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL  # 复用 DeepSeek 配置
from app.services.tools import TOOL_SCHEMAS  # 工具清单（与 Java AiToolRegistry 对齐）
from app.services.weather_query import is_weather_question  # 按本轮问句裁剪天气工具



class _DeepSeekChatOpenAI(ChatOpenAI):
    """DeepSeek 客户端：给带 tool_calls 的 assistant 消息补回 reasoning_content。

    deepseek-flash 默认走思考模式，接口要求「带 tool_calls 的 assistant 消息必须回传
    reasoning_content」，缺失即 400：
    The `reasoning_content` in the thinking mode must be passed back to the API.
    langchain-openai 1.6.2 既不提取流式 reasoning_content、也不写进请求体，
    只有本模块自拼的 assistant 工具消息会踩到；服务端只校验字段存在，补空串即可。
    """

    def _get_request_payload(self, input_, *, stop=None, **kwargs):
        payload = super()._get_request_payload(input_, stop=stop, **kwargs)
        for message in payload.get("messages") or []:
            if message.get("role") == "assistant" and message.get("tool_calls"):
                message.setdefault("reasoning_content", "")
        return payload


# 延迟创建：不在 import 时构造——没有 API Key 的环境（如单测）连 import 都会失败。
_llm_lock = threading.Lock()
_chat_llm: ChatOpenAI | None = None
_tool_llm: ChatOpenAI | None = None


def _get_chat_llm() -> ChatOpenAI:
    """返回共享的对话客户端（首次调用创建，之后复用连接池）。"""
    global _chat_llm
    if _chat_llm is None:
        with _llm_lock:
            if _chat_llm is None:
                _chat_llm = _DeepSeekChatOpenAI(
                    model="deepseek-flash",  # DeepSeek 官方 Flash 快速对话模型（V4.1）
                    api_key=DEEPSEEK_API_KEY,  # API 密钥（复用 reasoning.py 配置）
                    base_url=DEEPSEEK_BASE_URL,  # 自定义 API 地址
                    temperature=0.2,  # 对话温度降低（0.7→0.2）：减少发散，答案更贴合资料与事实
                    max_tokens=2048,  # 回答最多 2048 token
                    streaming=True,  # 开启流式
                    request_timeout=120,  # 流式请求超时放宽到 120s
                )
    return _chat_llm


def _get_tool_llm(question: str = "") -> ChatOpenAI:
    """返回绑定了本轮工具清单的流式客户端（客户端本体单例，只重做工具绑定）。

    绑定工具清单、开启流式：每轮流式生成时边吐文本边累积 tool_call，无工具调用
    则该轮文本就是最终回答——一遍生成，省掉「先非流式判断再流式重答」的第二遍。
    温度降低使工具选择更稳定。

    get_weather 按本轮问题动态绑定：用户这句没提天气就不给模型这把工具。
    否则历史里残留的天气话题（「广州天气」后追问「查看合同」）会让模型误判
    用户还在聊天气，自行再查一遍广州并复述（实测串台）。
    """
    global _tool_llm
    if _tool_llm is None:
        with _llm_lock:
            if _tool_llm is None:
                _tool_llm = _DeepSeekChatOpenAI(
                    model="deepseek-flash",
                    api_key=DEEPSEEK_API_KEY,
                    base_url=DEEPSEEK_BASE_URL,
                    temperature=0.3,  # 工具选择更稳定
                    max_tokens=2048,
                    streaming=True,  # 流式：边吐文本边累积 tool_call，无工具调用即最终回答
                    request_timeout=120,  # 流式请求超时与 chat_llm 对齐
                )
    # bind_tools 只产出一个轻量 RunnableBinding，底层仍复用同一客户端与连接池
    return _tool_llm.bind_tools(_tools_for(question))


# 天气工具名（本轮不带天气话题时从工具清单剔除）
_WEATHER_TOOL_NAME = "get_weather"


def _tools_for(question: str) -> list[dict]:
    """按本轮问题裁剪工具清单：不带天气话题时移除 get_weather。

    不是安全边界，是防误用：模型手上没这把工具，就不会在查合同时顺手查天气。
    """
    if is_weather_question(question):
        return TOOL_SCHEMAS
    return [
        t for t in TOOL_SCHEMAS
        if t.get("function", {}).get("name") != _WEATHER_TOOL_NAME
    ]


def _kb_citation_sources(hits: list) -> list[dict]:
    """把知识库工具命中的条目转成前端引用格式（camelCase，与 CitationVO 对齐）。

    documentId/chunkIndex 用于前端「点引用跳转打开原文档」，缺失则前端只展示来源不跳转。
    """
    sources = []
    seen = set()
    for h in hits or []:
        if not isinstance(h, dict):
            continue
        src = h.get("source", "未知文档")
        section = h.get("section", "")
        key = (src, section)
        if key in seen:
            continue
        seen.add(key)
        sources.append({
            "documentId": h.get("document_id"),
            "fileName": src,
            "chunkIndex": h.get("chunk_index"),
            "sectionPath": section,
            "contentPreview": (h.get("content") or "")[:200],
        })
    return sources
