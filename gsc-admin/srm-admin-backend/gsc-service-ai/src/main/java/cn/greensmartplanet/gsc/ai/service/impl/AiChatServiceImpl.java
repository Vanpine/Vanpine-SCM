package cn.greensmartplanet.gsc.ai.service.impl;

import cn.greensmartplanet.gsc.ai.dto.ChatCancelDTO;
import cn.greensmartplanet.gsc.ai.config.AiStreamExecutorConfig;
import cn.greensmartplanet.gsc.ai.dto.ChatRequestDTO;
import cn.greensmartplanet.gsc.ai.dto.ImageDescriptionDTO;
import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.entity.AiMessageImage;
import cn.greensmartplanet.gsc.ai.entity.AiToolCall;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiToolCallMapper;
import cn.greensmartplanet.gsc.ai.service.AiChatImageService;
import cn.greensmartplanet.gsc.ai.service.AiChatService;
import cn.greensmartplanet.gsc.ai.support.AiChatImageDescService;
import cn.greensmartplanet.gsc.ai.support.AiContextCompactService;
import cn.greensmartplanet.gsc.ai.support.AiToolTokenService;
import cn.greensmartplanet.gsc.ai.support.TokenEstimator;
import cn.greensmartplanet.gsc.ai.vo.ChatImageVO;
import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话流式服务实现
 * <p>
 * 流程：建会话(如需) → 查历史 → 落库 user → 调 Python 流式 → 逐 chunk 转发 SseEmitter → 落库 assistant(+citations)。
 * <p>
 * 用 JDK HttpClient 读 Python SSE（servlet 栈不引入 webflux）。
 * 流式响应必须挂在当前 HTTP 连接上，不能改走 MQ；@Async 仅用于立刻返回 SseEmitter，与「事务后异步任务走 MQ」不是同一类场景。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiMessageImageMapper messageImageMapper;
    private final AiToolCallMapper toolCallMapper;
    private final AiChatImageService chatImageService;
    private final AiChatImageDescService imageDescService;
    private final OssService ossService;
    private final ObjectMapper objectMapper;
    private final AiToolTokenService aiToolTokenService;
    private final AiContextCompactService contextCompactService;
    /** Redis：会话级生成锁（同一会话同时只能有一个生成中） */
    private final StringRedisTemplate stringRedisTemplate;

    /** Python AI 服务地址，由 Nacos / application-dev.yml 注入，禁止代码内写死 */
    @Value("${gsc.ai.service.url}")
    private String aiServiceUrl;

    /** Python AI 服务鉴权 token（X-API-Key） */
    @Value("${gsc.ai.service.token}")
    private String aiServiceToken;

    /**
     * 历史消息 token 预算（{@link TokenEstimator} 的估算值），超出即丢最早的。
     * <p>默认值 3000 与 Nacos gsc-ai.yaml 的取值对齐——该配置此前只在远程 Nacos，
     * 用仓库模板全新部署会因缺 key 启动失败，故此处必须留兜底。
     * <p>⚠️ 口径说明：此前按 {@code content.length()} 字符数计，现改为与 Python 一致的加权估算。
     * 纯中文场景估算值约为字符数的 1.1 倍，窗口会比原来略小；如需维持原窗口大小，
     * 在 Nacos 把本项调大约 10% 即可。
     */
    @Value("${gsc.ai.context.history-token-budget:3000}")
    private int historyTokenBudget;

    /** 组装 LLM 上下文时先取多少条候选（再按 token 预算从最近往回裁） */
    private static final int HISTORY_FETCH_LIMIT = 50;
    /** assistant 历史回答的字符上限：与 Python 侧 MAX_HISTORY_MESSAGE_CHARS 对齐 */
    private static final int ASSISTANT_HISTORY_MAX_CHARS = 120;
    /** 截断标记，一并计入上限，保证截断结果不超过 ASSISTANT_HISTORY_MAX_CHARS */
    private static final String HISTORY_TRUNCATED_SUFFIX = "…（历史答案已截断）";
    /** 表格占位文案：回答一上来就是表格、没有结论句可留时用 */
    private static final String HISTORY_TABLE_ONLY_PLACEHOLDER = "（上一轮回复了一个表格，内容已省略）";
    /** 会话标题长度上限（首条消息前 20 字） */
    private static final int TITLE_MAX_LEN = 20;
    /** 连接超时（Python 服务不可用快速失败） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 读超时（与 SseEmitter 120s、网关响应超时对齐） */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    /** 单轮最多携带图片数（读图贵，先放开到 4 张） */
    private static final int MAX_IMAGES_PER_MESSAGE = 4;
    /** 图片外部 URL 长度上限（DeepSeek 侧限制 8192 字符） */
    private static final int MAX_IMAGE_URL_LEN = 8192;
    /** 纯图片提问时代入的兜底文案（同时用作会话标题） */
    private static final String DEFAULT_IMAGE_QUESTION = "请解析这张图片";
    /** 写工具二次确认短路、Python 未吐正文时，兜底落库的占位文案（防历史悬空） */
    private static final String CONFIRM_REQUIRED_PLACEHOLDER = "已发起操作，请确认执行。";
    /** 读 Python 流时轮询 streaming 占位的间隔：停止接口改写后尽快停读，避免再攒完整回答 */
    private static final long CANCEL_POLL_INTERVAL_MS = 200L;
    /** 超过此时长仍 streaming 视为进程残留，可清；之内视为另一页签正在生成 */
    private static final long STALE_STREAMING_SECONDS = 150L;
    /** 会话生成锁 key 前缀：同一会话同时只允许一个生成中 */
    private static final String GEN_LOCK_PREFIX = "ai:chat:gen:";
    /**
     * 会话上下文里最多保留的业务对象数（LRU）。
     * 取 10 而非 5：列表类工具一次可能返回 10 条，保留太少会让用户说「第 6 条」时定位不到。
     */
    private static final int MAX_CONTEXT_ENTITIES = 10;
    /**
     * 上下文实体的存活时长（2 小时）。
     * <p>防的是「隔天回来，旧对象还在，代词被消解到完全无关的对象上」。
     * 一次连续会话内的短期漂移由 LRU 上限兜住，不靠 TTL。
     */
    private static final long CONTEXT_ENTITY_TTL_MILLIS = 2 * 60 * 60 * 1000L;
    /** 实体写入时间戳字段名（epoch millis）。Python 侧 parse_entities 只取 title/objectType，多余字段自动忽略 */
    private static final String ENTITY_AT_FIELD = "at";
    /**
     * 单条用户提问的 token 上限（估算值，与 Python count_tokens 同口径）。
     * <p>
     * 不设限时用户粘贴整份合同就能把窗口预算挤爆：{@link #buildHistory} 至少保留最近一条，
     * 单条 3 万 token 会直接跟走，system + 工具 schema 之外再叠 3 万。
     * 2000 token 约等于 2000 汉字，足够描述任何一条业务诉求。
     */
    private static final int MAX_QUESTION_TOKENS = 2000;

    /**
     * 流式对话主流程。
     *
     * @param dto      前端请求（conversationId 可空，空则自动建会话；regenerate 仅在新回答 complete 时覆盖上一轮）
     * @param tenantId 租户 ID
     * @param userId   当前用户 ID
     * @param emitter  前端 SSE 输出
     */
    @Async(AiStreamExecutorConfig.STREAM_EXECUTOR_BEAN)
    @Override
    public void streamChat(ChatRequestDTO dto, Long tenantId, Long userId, SseEmitter emitter) {
        // 客户端断开（停止生成）与服务端主动 complete 必须分开，避免 complete() 把自己标成中断
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        AtomicBoolean serverCompleted = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            if (!serverCompleted.get()) {
                clientDisconnected.set(true);
            }
        });
        emitter.onTimeout(() -> clientDisconnected.set(true));
        emitter.onError(e -> clientDisconnected.set(true));

        String content = dto.getContent();
        boolean regenerate = Boolean.TRUE.equals(dto.getRegenerate());
        // 提前声明，catch 里异常断开时也能把已生成片段落库
        Long conversationId = dto.getConversationId();
        Long replaceAssistantId = null;
        Long assistantMessageId = null;
        StringBuilder answerBuf = new StringBuilder();
        String citationsJson = null;
        String toolCallsJson = null;
        // complete() 若抛错会进 catch，避免同一轮 assistant 落两次
        boolean answerPersisted = false;
        // 是否持有会话生成锁（finally 里据此释放，没抢到不删，避免误删别人的锁）
        boolean genLockHeld = false;
        // 写工具二次确认短路：本轮发生过 confirm_required，收尾 answerBuf 为空也不删占位
        AtomicBoolean confirmRequired = new AtomicBoolean(false);
        try {
            // 1. 入参：普通提问 content/imageIds 至少一个；重生成必须已有会话
            List<Long> imageIds = parseImageIds(dto.getImageIds());
            if (regenerate) {
                if (dto.getConversationId() == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "重新生成必须指定会话");
                }
            } else if ((content == null || content.isBlank()) && imageIds.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "提问内容不能为空");
            }
            // 1.1 单条提问 token 上限：超限的输入会挤爆历史窗口与总上下文预算，直接拒绝而不是静默截断
            requireQuestionWithinLimit(content);

            // 2. 会话归属：重生成/追问都要校验租户+用户，防越权写他人会话
            String contextEntities = null;
            String historySummary = null;
            if (conversationId == null) {
                String titleSource = (content == null || content.isBlank()) ? DEFAULT_IMAGE_QUESTION : content;
                conversationId = createConversation(tenantId, userId, titleSource);
            } else {
                AiConversation existing = conversationMapper.selectByIdAndTenantUser(conversationId, tenantId, userId);
                if (existing == null) {
                    throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
                }
                // 业务对象上下文：供 AI 指代消解（追问「第一条/刚才那个」时恢复对象），仅内部理解
                contextEntities = existing.getContextEntities();
                // 早前对话摘要：滑动窗口外的老对话压缩而成，拼在最近对话之前喂给模型
                historySummary = existing.getSummary();
            }

            // 3. 会话级互斥：同一会话同时只能有一个生成中。
            //    先抢 Redis 锁（原子），抢不到直接拒绝——必须放在清理/查库之前，
            //    否则未持锁的请求会把在途生成（<150s）的占位误删。
            Boolean genLock = tryAcquireGenLock(conversationId);
            if (Boolean.FALSE.equals(genLock)) {
                throw new BizException(ErrorCode.AI_CHAT_BUSY);
            }
            // Redis 不可用时降级为下面的查库判断（可用性优先，不因缓存故障挡住对话）
            genLockHeld = Boolean.TRUE.equals(genLock);

            // 3.1 清超时残留的 streaming 占位（进程崩溃留下的；150s 之内视为在途）
            messageMapper.deleteStaleStreamingByConversation(
                    conversationId, tenantId, LocalDateTime.now().minusSeconds(STALE_STREAMING_SECONDS));
            // 3.2 库里已有生成中的占位也拒绝：Redis 降级时的兜底，同时防实例崩溃残留
            if (messageMapper.selectLatestStreaming(conversationId, tenantId) != null) {
                throw new BizException(ErrorCode.AI_CHAT_BUSY);
            }

            // 4. 重生成：复用上一轮 user，不重复插提问；旧 assistant 仅在新回答 complete 后物理删
            if (regenerate) {
                AiMessage lastUser = messageMapper.selectLatestByRole(conversationId, tenantId, "user");
                if (lastUser == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "没有可重新生成的提问");
                }
                content = lastUser.getContent();
                if (imageIds.isEmpty()) {
                    imageIds = resolveImageIdsOfMessage(lastUser.getId(), tenantId);
                }
                AiMessage lastAssistant = messageMapper.selectLatestByRole(conversationId, tenantId, "assistant");
                if (lastAssistant != null) {
                    replaceAssistantId = lastAssistant.getId();
                }
            }

            // 用户没打字时库里保持空文案，避免气泡出现「请解析这张图片」；喂模型时才用兜底句
            String persistContent = content == null ? "" : content;
            String llmQuestion = persistContent.isBlank() ? DEFAULT_IMAGE_QUESTION : persistContent;

            // 5. 图片归属校验 + 现签短效 URL（DeepSeek 只能拉公网 URL）
            List<String> imageUrls = toSignedImageUrls(imageIds, tenantId, userId, conversationId);

            // 6. 首帧回传会话 ID（雪花转字符串，避免 JS Number 精度丢失）
            sendEvent(emitter, objectMapper.writeValueAsString(Map.of(
                    "type", "conversation",
                    "conversationId", String.valueOf(conversationId))), clientDisconnected);

            // 7. 组装历史：重生成时排除旧 assistant，避免模型复述被否答案
            HistoryResult historyResult = buildHistory(conversationId, tenantId, replaceAssistantId);
            List<Map<String, Object>> history = historyResult.messages();

            // 7.1 有老消息掉出窗口 → 异步压缩进摘要（旁路，不阻塞本轮回答；
            //     压好的摘要下一轮才用得上，本轮仍用旧摘要）
            triggerCompactionIfTrimmed(conversationId, tenantId, historyResult.trimmed(),
                    historyResult.windowStartMessageId(), history.size());

            // 8. 普通提问才落库 user；重生成沿用上一轮 user，避免历史重复
            if (!regenerate) {
                persistUserMessage(conversationId, tenantId, userId, persistContent, imageIds);
            }

            // 9. 先插 streaming 占位：停止接口据此 CAS 写成 cancelled，流结束不能再覆盖成 complete
            assistantMessageId = persistStreamingPlaceholder(conversationId, tenantId);

            // 9.1 回传本条 assistant 消息的真实 ID（雪花转字符串）：
            // 前端流式结束后用它做消息 id，才能对刚生成完的回答提交反馈/定位单条消息；
            // 否则前端只能编临时 id，反馈接口收到非数字 id 会 500。
            sendEvent(emitter, objectMapper.writeValueAsString(Map.of(
                    "type", "message",
                    "messageId", String.valueOf(assistantMessageId))), clientDisconnected);

            // 10. 调 Python 流式生成（慢 I/O 不进事务，避免长事务锁表）
            StreamResult streamResult = callPythonStream(
                    conversationId, tenantId, userId, assistantMessageId, llmQuestion, history, imageUrls,
                    contextEntities, historySummary, emitter, answerBuf, clientDisconnected, confirmRequired);
            citationsJson = streamResult.citationsJson();
            toolCallsJson = streamResult.toolCallsJson();

            // 11. 正常结束或客户端停止：CAS 收尾占位（中断写 cancelled，重生成失败不覆盖旧答）
            persistStreamResult(conversationId, tenantId, assistantMessageId, answerBuf, citationsJson, toolCallsJson,
                    replaceAssistantId, clientDisconnected.get(), false, confirmRequired.get());
            answerPersisted = true;

            // 12. 客户端已断开则 emitter 已结束，不能再 complete
            if (clientDisconnected.get()) {
                log.info("流式对话被客户端中断: conversationId={}, answer_len={}", conversationId, answerBuf.length());
                return;
            }
            serverCompleted.set(true);
            emitter.complete();
            log.info("流式对话完成: conversationId={}, answer_len={}", conversationId, answerBuf.length());
        } catch (Exception e) {
            // 读流抛 IO 时也可能已断开，先尽量落半段，避免界面有字库里没有
            if (!answerPersisted) {
                persistStreamResult(conversationId, tenantId, assistantMessageId, answerBuf, citationsJson, toolCallsJson,
                        replaceAssistantId, clientDisconnected.get(), true, confirmRequired.get());
            }
            if (clientDisconnected.get()) {
                log.info("流式对话中断后结束: tenantId={}", tenantId);
                return;
            }
            log.error("流式对话异常: tenantId={}", tenantId, e);
            try {
                String errMsg = (e instanceof BizException && e.getMessage() != null && !e.getMessage().isBlank())
                        ? e.getMessage()
                        : "AI 服务异常，请稍后重试";
                emitter.send(objectMapper.writeValueAsString(Map.of("type", "error", "message", errMsg)));
                serverCompleted.set(true);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        } finally {
            // 释放会话生成锁，让本会话能开始下一轮（失败也不怕，TTL 到期自动过期）
            releaseGenLock(conversationId, genLockHeld);
        }
    }

    /**
     * 抢会话生成锁（Redis 原子占位），多实例 / 多线程下保证同一会话同时只有一个生成中。
     *
     * <p>为什么不能只靠 {@code selectLatestStreaming} 查库：那是「先查后插」，
     * 从查库到第 9 步插占位之间隔着图片签名（HTTP）、历史组装、异步压缩、落 user 消息，
     * 窗口很大，并发下两个请求都能通过检查、各自插一条 streaming 占位。
     *
     * @return {@code TRUE}=抢到；{@code FALSE}=已被别的请求占用；{@code null}=Redis 不可用（调用方降级查库）
     */
    Boolean tryAcquireGenLock(Long conversationId) {
        try {
            return stringRedisTemplate.opsForValue().setIfAbsent(
                    GEN_LOCK_PREFIX + conversationId, "1",
                    Duration.ofSeconds(SystemConstants.AI_CHAT_GEN_LOCK_TTL));
        } catch (Exception e) {
            log.warn("会话生成锁获取失败，降级为查库判断: conversationId={}, error={}",
                    conversationId, e.getMessage());
            return null;
        }
    }

    /** 释放会话生成锁；未持锁（Redis 降级）时不动，避免误删别人的锁（包级可见便于单测） */
    void releaseGenLock(Long conversationId, boolean held) {
        if (!held || conversationId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(GEN_LOCK_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("释放会话生成锁失败（TTL 到期自动过期）: conversationId={}, error={}",
                    conversationId, e.getMessage());
        }
    }

    /**
     * 停止生成：归属校验后 CAS 收口 streaming 占位。
     * 重生成停止删占位、保留旧答；无半段正文也不留空气泡。
     *
     * @param dto      会话ID + 半段正文 + 是否重生成
     * @param tenantId 租户ID
     * @param userId   当前用户
     */
    @Override
    public void cancelChat(ChatCancelDTO dto, Long tenantId, Long userId) {
        Long conversationId = dto.getConversationId();
        if (conversationId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "会话ID不能为空");
        }
        // 1. 归属校验：只能停自己的会话
        AiConversation existing = conversationMapper.selectByIdAndTenantUser(conversationId, tenantId, userId);
        if (existing == null) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        // 2. 没有进行中的占位：流可能已结束，幂等返回
        AiMessage streaming = messageMapper.selectLatestStreaming(conversationId, tenantId);
        if (streaming == null) {
            log.info("停止生成：没有进行中的回答 conversationId={}", conversationId);
            return;
        }
        boolean regenerate = Boolean.TRUE.equals(dto.getRegenerate());
        String content = dto.getContent();
        // 3. 重生成或还没吐字：删占位，刷新后仍是上一篇完整回答 / 不出现空气泡
        if (regenerate || content == null || content.isBlank()) {
            // 带 status 守卫：SELECT 到 DELETE 之间流可能已完成并写入正文，无守卫会误删完整回答
            messageMapper.deleteStreamingByIdAndConversation(streaming.getId(), conversationId, tenantId);
            log.info("停止生成已删除占位: conversationId={}, regenerate={}", conversationId, regenerate);
            return;
        }
        // 4. CAS 写成 cancelled；引用来源优先用前端已展示的，否则用占位行上已写入的。
        //    停止时工具轨迹沿用占位行上已写入的（流式过程中未单独增量写，故传 null 表示不改）
        int updated = messageMapper.finalizeStreaming(
                streaming.getId(), conversationId, tenantId, content,
                resolveCancelCitationsJson(dto, streaming), streaming.getToolCallsJson(), "cancelled");
        if (updated > 0) {
            AiConversation touch = new AiConversation();
            touch.setId(conversationId);
            conversationMapper.updateById(touch);
        }
        log.info("停止生成已落库: conversationId={}, updated={}, len={}", conversationId, updated, content.length());
    }

    /**
     * 写操作确认/取消落库后，覆盖本条 assistant 气泡的「已发起操作」占位为最终文案。
     * 只定向更新单条消息的 content，其余字段不动。
     *
     * @param assistantMessageId 流式阶段落库的 assistant 消息ID
     * @param tenantId           当前租户
     * @param finalText          最终展示文案
     */
    @Override
    public void updateConfirmResult(Long assistantMessageId, Long tenantId, String finalText) {
        // 直连验证等无前端消息的路径 assistantMessageId 为空，跳过覆盖
        if (assistantMessageId == null || finalText == null || finalText.isBlank()) {
            return;
        }
        int updated = messageMapper.updateContentById(assistantMessageId, tenantId, finalText);
        log.info("写操作确认结果已覆盖气泡: assistantMessageId={}, updated={}", assistantMessageId, updated);
    }

    /**
     * 解析并校验图片ID列表：超过上限或非法字符串直接拒绝（不静默忽略）。
     *
     * @param raw 前端传入的字符串 ID
     * @return 解析后的雪花 ID
     */
    private List<Long> parseImageIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> ids = raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return Long.valueOf(s.trim());
                    } catch (NumberFormatException e) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "图片ID格式错误");
                    }
                })
                .toList();
        if (ids.size() > MAX_IMAGES_PER_MESSAGE) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单次最多上传 " + MAX_IMAGES_PER_MESSAGE + " 张图片");
        }
        return ids;
    }

    /**
     * 读取某条 user 消息已关联的图片 ID（重生成未再传 imageIds 时复用原图）。
     *
     * @param messageId 消息ID
     * @param tenantId  租户ID
     * @return 图片 ID 列表
     */
    private List<Long> resolveImageIdsOfMessage(Long messageId, Long tenantId) {
        Map<Long, List<ChatImageVO>> imageMap = chatImageService.listByMessageIds(List.of(messageId), tenantId);
        List<ChatImageVO> images = imageMap.getOrDefault(messageId, List.of());
        // VO 的 id 已序列化为字符串，转回 Long 才能走归属校验
        return images.stream()
                .map(ChatImageVO::getId)
                .filter(id -> id != null && !id.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    /**
     * 校验图片归属并现签短效 URL。
     *
     * @param imageIds       图片ID
     * @param tenantId       租户ID
     * @param userId         当前用户
     * @param conversationId 仅用于日志
     * @return DeepSeek 可访问的签名 URL
     */
    private List<String> toSignedImageUrls(List<Long> imageIds, Long tenantId, Long userId, Long conversationId) {
        return imageIds.stream().map(imageId -> {
            String ossKey = chatImageService.getOwnedOssKey(imageId, tenantId, userId);
            String signedUrl = ossService.generateGetUrl(ossKey);
            if (signedUrl.length() > MAX_IMAGE_URL_LEN) {
                throw new BizException(ErrorCode.BAD_REQUEST, "图片地址过长，请重新上传");
            }
            log.info("对话携带图片: conversationId={}, imageId={}", conversationId, imageId);
            return signedUrl;
        }).toList();
    }

    /**
     * 首次提问时新建会话，标题取内容前 20 字。
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param content  标题来源
     * @return 新会话雪花 ID
     */
    private Long createConversation(Long tenantId, Long userId, String content) {
        String title = content.length() > TITLE_MAX_LEN ? content.substring(0, TITLE_MAX_LEN) : content;
        AiConversation conv = AiConversation.builder()
                .tenantId(tenantId)
                .userId(userId)
                .title(title)
                .status("active")
                .build();
        conversationMapper.insert(conv);
        return conv.getId();
    }

    /**
     * 组装 LLM 上下文历史（从最近往回凑够 token 预算就停）。
     * <p>
     * 不再按固定条数：一条 50 字的短问和一条 2000 字的长答占的位置差 40 倍，
     * 固定条数会让窗口忽大忽小。改成「从最近往回累加，凑够预算就停」，
     * 长消息自然少带几条、短消息多带几条。
     * <p>
     * 只带文字：图很贵，默认不把历史图片全部塞进去。
     * 用户要点哪张，由本轮 imageIds 现签后放进当前提问。
     *
     * @param conversationId   会话ID
     * @param tenantId         租户ID
     * @param excludeMessageId 重生成时排除旧 assistant，避免复述；普通提问传 null
     * @return 窗口内消息 + 是否发生裁剪（裁剪即有老消息掉出窗口，需要压缩兜底）
     */
    HistoryResult buildHistory(Long conversationId, Long tenantId, Long excludeMessageId) {
        // 先多取一批候选，再按预算裁：一次 SQL 拿够，避免为了凑预算反复查库
        List<AiMessage> fetched = messageMapper.selectRecentByConversation(
                conversationId, tenantId, HISTORY_FETCH_LIMIT);
        // 批量取图片上下文（≤50 条一次查询）：既拿描述，也拿「哪些消息带图」。
        // 必须在过滤之前拿——纯图片提问的消息 content 是空串，会被下面的内容过滤误杀。
        List<Long> fetchedIds = fetched.stream().map(AiMessage::getId).toList();
        AiChatImageDescService.ImageContext imageCtx =
                imageDescService.loadImageContext(fetchedIds, tenantId);
        List<AiMessage> candidates = fetched.stream()
                .filter(m -> excludeMessageId == null || !excludeMessageId.equals(m.getId()))
                // 只把完整回答喂给模型，避免半段 cancelled/error 被当成上文续写
                .filter(m -> "user".equals(m.getRole())
                        || ("assistant".equals(m.getRole()) && "complete".equals(m.getStatus())))
                // 内容非空，或带图——纯图片提问（用户只发图不打字）的 content 是空串，
                // 但语义上不空；漏掉它们会让图片描述永不生成、历史里也看不到图
                .filter(m -> (m.getContent() != null && !m.getContent().isBlank())
                        || imageCtx.messageIdsWithImages().contains(m.getId()))
                .toList();
        Map<Long, List<ImageDescriptionDTO>> imageDescMap = imageCtx.descriptionsByMessage();

        // 从最近往回累加；至少保留最近一条，避免单条就超预算时把历史清空
        List<Map<String, Object>> kept = new ArrayList<>();
        int used = 0;
        Long windowStartMessageId = null;   // 窗口里最早那条的 id，倒序累加结束后即为最终值
        for (int i = candidates.size() - 1; i >= 0; i--) {
            AiMessage message = candidates.get(i);
            // assistant 长回答先截断再计预算：Python 侧送模型的就是截断后的内容，
            // 不截断会让预算被长回答白占（Java 按全文计费、Python 按截断后送模型，两边账对不上）
            String content = "assistant".equals(message.getRole())
                    ? trimAssistantHistory(message.getContent())
                    : message.getContent();
            List<ImageDescriptionDTO> imageDescs = imageDescMap.get(message.getId());
            int descTokens = imageDescs == null ? 0
                    : imageDescs.stream().mapToInt(d -> TokenEstimator.count(d.getDescription())).sum();
            int cost = TokenEstimator.count(content) + descTokens;
            if (!kept.isEmpty() && used + cost > historyTokenBudget) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", content);
            // 历史轮次不带原图（token 太贵），用描述替代，让「刚才那张图/第 2 张」仍可指代
            if (imageDescs != null) {
                item.put("imageDescriptions", imageDescs);
            }
            kept.add(item);
            windowStartMessageId = message.getId();
            used += cost;
        }
        Collections.reverse(kept);  // 累加是倒着来的，还原成时间升序
        // 兜底补生成：正常路径在上传时已触发，这里只兜「上传时没生成成功 / 历史遗留」。
        // 用 fetchedIds 而非候选集——纯图片提问的消息会被「内容非空」过滤掉，
        // 若按候选集传，它们的图永远拿不到描述。
        imageDescService.ensureAsyncByMessages(fetchedIds, tenantId);
        // trimmed 有两个来源，任一成立都要触发压缩兜底：
        //   1) 候选内部被预算裁掉（kept < candidates）
        //   2) 候选本身被 SQL 上限截断（取满 FETCH_LIMIT）——此时可能有更老的消息
        //      既没进窗口、也没进摘要，不标记就会静默丢失
        boolean fetchTruncated = fetched.size() >= HISTORY_FETCH_LIMIT;
        boolean trimmed = kept.size() < candidates.size() || fetchTruncated;
        // 裁剪原因分开记：是「预算装不下」还是「SQL 只取了 50 条」，排查时性质不同
        if (trimmed) {
            log.info("历史窗口裁剪: conversationId={}, 候选={}条, 装入={}条, 用量={}/{} token, "
                            + "窗口起点={}, SQL取满={}",
                    conversationId, candidates.size(), kept.size(), used, historyTokenBudget,
                    windowStartMessageId, fetchTruncated);
        }
        return new HistoryResult(kept, trimmed, windowStartMessageId);
    }

    /**
     * 截断 assistant 历史回答：按段落累加保留结论句，遇到 markdown 表格整块丢弃。
     *
     * <p>为什么放在 Java 而不是 Python：预算裁剪在 {@link #buildHistory} 里按 content.length() 计，
     * 这里不截断的话 Java 按全文计费、Python 却按截断后长度送模型，两边账对不上，预算被长回答白占。
     *
     * <p>结果长度恒 ≤ {@link #ASSISTANT_HISTORY_MAX_CHARS}，因此 Python 侧同名截断成为空操作
     * （保留作兜底，不会二次截断）。
     *
     * @param content assistant 原文
     * @return 截断后的文本；未超限则原样返回
     */
    String trimAssistantHistory(String content) {
        if (content == null || content.length() <= ASSISTANT_HISTORY_MAX_CHARS) {
            return content;
        }
        // 后缀本身也占字符，先扣掉，保证最终结果不超上限
        int budget = ASSISTANT_HISTORY_MAX_CHARS - HISTORY_TRUNCATED_SUFFIX.length();
        List<String> kept = new ArrayList<>();
        int used = 0;
        for (String para : content.split("\n\n", -1)) {
            if (isTableBlock(para)) {
                break;  // 表格整块丢弃，不切一半（半截表格会让模型把「50」当成完整数字读）
            }
            int cost = para.length() + (kept.isEmpty() ? 0 : 2);  // 段落间以 "\n\n" 相连
            if (!kept.isEmpty() && used + cost > budget) {
                break;
            }
            kept.add(para);
            used += cost;
        }
        if (kept.isEmpty()) {
            // 一上来就是表格，没有可保留的结论句
            return HISTORY_TABLE_ONLY_PLACEHOLDER;
        }
        String result = String.join("\n\n", kept);
        if (result.length() > budget) {
            // 首段本身就超预算（无空行分段的超长回答，如长列表）：硬切兜底。
            // 必须保证 ≤ 上限，否则 Python 侧会再截一次，两边账又对不上。
            result = result.substring(0, budget);
        }
        return result + HISTORY_TRUNCATED_SUFFIX;
    }

    /** 是否 markdown 表格块（块内有以 | 开头的行） */
    private boolean isTableBlock(String text) {
        for (String line : text.split("\n", -1)) {
            if (line.stripLeading().startsWith("|")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 历史窗口结果：窗口内消息 + 是否发生裁剪（包级可见，便于单测）。
     *
     * @param messages             窗口内消息（时间升序）
     * @param trimmed              true 表示有老消息掉出窗口（需触发压缩兜底）
     * @param windowStartMessageId 窗口里最早那条消息的 ID，作为压缩的上界（窗口内的原文不能压，
     *                             否则会与摘要重复）；窗口为空时为 null
     */
    record HistoryResult(List<Map<String, Object>> messages, boolean trimmed, Long windowStartMessageId) {
    }

    /**
     * 落库 user 消息及图片关联。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param userId         用户ID
     * @param content        提问正文
     * @param imageIds       本轮图片
     */
    private void persistUserMessage(Long conversationId, Long tenantId, Long userId,
                                    String content, List<Long> imageIds) {
        AiMessage userMsg = AiMessage.builder()
                .conversationId(conversationId)
                .tenantId(tenantId)
                .userId(userId)
                .role("user")
                .content(content)
                .status("complete")
                .build();
        messageMapper.insert(userMsg);
        imageIds.forEach(imageId -> messageImageMapper.insert(AiMessageImage.builder()
                .messageId(userMsg.getId())
                .imageId(imageId)
                .tenantId(tenantId)
                .build()));
    }

    /**
     * 流结束 CAS 收尾 streaming 占位。停止接口可能已经写成 cancelled，此时更新 0 行。
     *
     * @param conversationId     会话ID，尚未建出会话时为 null，直接跳过
     * @param tenantId           租户ID
     * @param assistantMessageId streaming 占位 ID，未插入时为 null
     * @param answerBuf          已生成片段
     * @param citationsJson      引用来源数组 JSON，可空
     * @param replaceAssistantId 重生成要覆盖的旧 assistant，普通提问为 null
     * @param disconnected       客户端已断开或停止接口已改写占位
     * @param failed             生成过程抛错（非主动停止）
     * @param confirmRequired    写工具二次确认短路（answerBuf 空时保底写占位，防历史悬空）
     */
    private void persistStreamResult(Long conversationId, Long tenantId, Long assistantMessageId,
                                     StringBuilder answerBuf, String citationsJson, String toolCallsJson,
                                     Long replaceAssistantId,
                                     boolean disconnected, boolean failed, boolean confirmRequired) {
        if (conversationId == null || assistantMessageId == null) {
            return;
        }
        // 重生成失败或中途停止：删占位、不覆盖旧答（停止接口也可能已删）
        if (replaceAssistantId != null && (disconnected || failed)) {
            // 带 status 守卫：停止接口可能已把它 CAS 成 cancelled 并写入半段正文
            messageMapper.deleteStreamingByIdAndConversation(assistantMessageId, conversationId, tenantId);
            log.info("重生成未成功，保留旧回答: conversationId={}, disconnected={}, failed={}",
                    conversationId, disconnected, failed);
            return;
        }
        // 还没吐字：不留空气泡；但写工具二次确认短路时保底写占位，防历史悬空（有 user 无 assistant）
        if (answerBuf == null || answerBuf.isEmpty()) {
            if (!confirmRequired) {
                // 带 status 守卫：若停止接口已落库半段正文，这里不能删（0 行即表示已收尾）
                messageMapper.deleteStreamingByIdAndConversation(assistantMessageId, conversationId, tenantId);
                return;
            }
            // 占位文案塞回 answerBuf 走下方统一收尾，后续确认/取消再覆盖成最终文案
            answerBuf.append(CONFIRM_REQUIRED_PLACEHOLDER);
        }
        String status = disconnected ? "cancelled" : (failed ? "error" : "complete");
        int updated = messageMapper.finalizeStreaming(
                assistantMessageId, conversationId, tenantId, answerBuf.toString(), citationsJson, toolCallsJson, status);
        if (updated == 0) {
            log.info("助手消息已被停止接口落库，跳过覆盖: conversationId={}, id={}",
                    conversationId, assistantMessageId);
            return;
        }
        // 只有新回答完整完成才物理删旧 assistant（本表无 is_deleted）
        if (replaceAssistantId != null && "complete".equals(status)) {
            messageMapper.deleteByIdAndConversation(replaceAssistantId, conversationId, tenantId);
        }
        // 会话列表按 update_time 倒序，有新内容才续命
        AiConversation touch = new AiConversation();
        touch.setId(conversationId);
        conversationMapper.updateById(touch);
    }

    /**
     * 插入 streaming 占位，供停止接口与流结束 CAS 争用同一行。
     *
     * @param conversationId 会话ID
     * @param tenantId       租户ID
     * @param content        完整或中断后的片段
     * @param citationsJson  引用来源数组 JSON，可空
     * @param status         complete / cancelled / error
     */
    private Long persistStreamingPlaceholder(Long conversationId, Long tenantId) {
        AiMessage assistantMsg = AiMessage.builder()
                .conversationId(conversationId)
                .tenantId(tenantId)
                .userId(null)
                .role("assistant")
                .content("")
                .status("streaming")
                .build();
        messageMapper.insert(assistantMsg);
        return assistantMsg.getId();
    }

    /**
     * 占位已被停止接口改写或删除，则应停读 Python。
     *
     * @param assistantMessageId 占位 ID
     * @param tenantId           租户ID
     * @return true 表示不应再继续生成
     */
    private boolean isStreamingAborted(Long assistantMessageId, Long tenantId) {
        if (assistantMessageId == null) {
            return false;
        }
        AiMessage row = messageMapper.selectByIdAndTenant(assistantMessageId, tenantId);
        return row == null || !"streaming".equals(row.getStatus());
    }

    /**
     * 生成中途把引用来源写到 streaming 占位，关页停止时前端即使没带上也能落库。
     */
    private void persistStreamingCitations(Long assistantMessageId, Long tenantId, String citationsJson) {
        if (assistantMessageId == null || citationsJson == null || citationsJson.isBlank()) {
            return;
        }
        messageMapper.updateStreamingCitations(assistantMessageId, tenantId, citationsJson);
    }

    /**
     * 停止落库用的 citations_json：前端已展示的优先，否则用占位行。
     */
    private String resolveCancelCitationsJson(ChatCancelDTO dto, AiMessage streaming) {
        if (dto.getCitations() != null && !dto.getCitations().isEmpty()) {
            try {
                return objectMapper.writeValueAsString(dto.getCitations());
            } catch (Exception e) {
                log.warn("停止生成引用来源序列化失败: {}", e.getMessage());
            }
        }
        return streaming.getCitationsJson();
    }

    /**
     * 调 Python /api/chat（SSE 流），逐行解析 data: 事件。
     * 客户端断开时关闭 InputStream，让 Python 侧生成协程被取消。
     * <p>
     * 同时签一次性工具令牌随请求体下发给 Python，使 Python 能在 tool loop 里
     * 回调 /internal/ai/tool 查询业务数据；新增的 tool_call/tool_result 事件透传给前端，
     * 并在 tool_result 到达时落一条 ai_tool_calls 审计。
     *
     * @param conversationId 会话ID（工具审计落库用）
     * @return 流式结果：引用来源 JSON + 工具调用轨迹 JSON（均可能为 null）
     */
    /**
     * 校验单条提问不超过 {@link #MAX_QUESTION_TOKENS}，超限抛业务异常。
     * <p>
     * 包级可见便于单测：上限值是常量，真正要锁住的是「校验确实被调用且按 token 而非字符判」。
     *
     * @param content 用户提问正文（可为 null）
     */
    void requireQuestionWithinLimit(String content) {
        int tokens = TokenEstimator.count(content);
        if (tokens > MAX_QUESTION_TOKENS) {
            log.warn("提问超过 token 上限被拒: tokens={}, limit={}", tokens, MAX_QUESTION_TOKENS);
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "提问内容过长（约 " + tokens + " tokens，上限 " + MAX_QUESTION_TOKENS + "），请精简后重试");
        }
    }

    /**
     * 历史窗口发生裁剪时触发异步压缩兜底（包级可见，便于单测接缝）。
     *
     * <p>这是「算窗口」与「压缩摘要」之间的接缝：{@link #buildHistory} 只负责算出
     * trimmed 与窗口起点，真正决定「要不要压、拿什么上界压」在这里——参数传错
     * （比如漏传 windowStartMessageId）两个方法各自的单测都是绿的。
     *
     * @return true=已触发压缩；false=未裁剪，无需压缩
     */
    boolean triggerCompactionIfTrimmed(Long conversationId, Long tenantId, boolean trimmed,
                                       Long windowStartMessageId, int windowSize) {
        if (!trimmed) {
            return false;
        }
        // 只打出「谁触发、窗口装了几条、截断点在哪、预算多少」，便于线上核对压缩是否真的被触发
        log.info("历史超出预算已裁剪，触发异步压缩: conversationId={}, 窗口内={}条, 窗口起点消息={}, "
                        + "预算={} token",
                conversationId, windowSize, windowStartMessageId, historyTokenBudget);
        contextCompactService.compactAsync(conversationId, tenantId, windowStartMessageId);
        return true;
    }

    /**
     * 组装发给 Python {@code /api/chat} 的请求体（包级可见，便于单测）。
     *
     * <p>字段名必须与 Python 侧 {@code ChatRequest}（schemas.py，蛇形）严格一致——
     * 写错一个字母 Python 就读不到，而且不会报错，只会静默失效。
     *
     * @param content         本轮提问（已做图片兜底文案替换）
     * @param history         滑动窗口内的历史消息
     * @param imageUrls       图片短效 URL，空则不下发
     * @param contextEntities 业务对象上下文 JSON 串；空则不下发
     * @param historySummary  早前对话摘要；空则不下发
     * @return 请求体（字段顺序稳定）
     */
    Map<String, Object> buildChatRequestBody(Long tenantId, Long userId, Long assistantMessageId,
                                             String content, List<Map<String, Object>> history,
                                             List<String> imageUrls, String contextEntities,
                                             String historySummary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", content);
        body.put("tenant_id", tenantId);
        body.put("history", history);
        if (imageUrls != null && !imageUrls.isEmpty()) {
            body.put("images", imageUrls.stream().map(url -> Map.of("url", url)).toList());
        }
        // 会话业务对象上下文（JSON 字符串，可能为 null）：供 Python 侧指代消解，仅内部理解
        if (contextEntities != null && !contextEntities.isBlank()) {
            body.put("context_entities", contextEntities);
        }
        // 早前对话摘要（可能为 null）：Python 侧拼在 system 之后、最近对话之前
        if (historySummary != null && !historySummary.isBlank()) {
            body.put("history_summary", historySummary);
        }
        // 签一次性工具令牌：让 Python 回调 Java 工具 API 时携带身份，模型/中间服务无法伪造。
        // 图片提问不涉及业务数据查询，但仍签发无妨（Python 侧对图片走纯对话分支）。
        body.put("tool_token", aiToolTokenService.generateToken(
                assistantMessageId, tenantId, userId));
        return body;
    }

    private StreamResult callPythonStream(Long conversationId, Long tenantId, Long userId, Long assistantMessageId, String content,
                                    List<Map<String, Object>> history, List<String> imageUrls,
                                    String contextEntities, String historySummary,
                                    SseEmitter emitter, StringBuilder answerBuf,
                                    AtomicBoolean clientDisconnected,
                                    AtomicBoolean confirmRequired) throws Exception {
        String requestJson = objectMapper.writeValueAsString(buildChatRequestBody(
                tenantId, userId, assistantMessageId, content, history, imageUrls,
                contextEntities, historySummary));

        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/api/chat"))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-API-Key", aiServiceToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new BizException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        String citationsJson = null;
        long lastPollAt = 0L;
        // 工具审计：缓存最近一次 tool_call 的工具名+参数，等配对的 tool_result 到达时落 ai_tool_calls；
        // 同时累积「回显轨迹」到 toolCalls，收尾写入 ai_messages.tool_calls_json（供历史展示 AI 查了什么）
        ToolCallAudit pendingToolCall = null;
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long now = System.currentTimeMillis();
                if (now - lastPollAt >= CANCEL_POLL_INTERVAL_MS) {
                    lastPollAt = now;
                    // 停止接口已改写/删除占位：不再继续读 Python，避免 answerBuf 攒满完整回答
                    if (isStreamingAborted(assistantMessageId, tenantId)) {
                        clientDisconnected.set(true);
                    }
                }
                if (clientDisconnected.get()) {
                    log.info("客户端已断开或用户已停止，停止读取 Python 流");
                    break;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String json = trimmed.substring(5).trim();
                if (json.isEmpty()) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(json);
                String type = node.path("type").asText();
                switch (type) {
                    case "chunk" -> {
                        String chunk = node.path("content").asText("");
                        if (!chunk.isEmpty()) {
                            answerBuf.append(chunk);
                            sendEvent(emitter, json, clientDisconnected);
                        }
                    }
                    case "citations" -> {
                        JsonNode sources = node.path("sources");
                        citationsJson = sources.isArray() ? sources.toString() : "[]";
                        persistStreamingCitations(assistantMessageId, tenantId, citationsJson);
                        sendEvent(emitter, json, clientDisconnected);
                    }
                    case "done" -> sendEvent(emitter, json, clientDisconnected);
                    // 写工具二次确认短路：标记本轮有确认，收尾 answerBuf 为空也不删占位
                    case "confirm_required" -> {
                        confirmRequired.set(true);
                        // 写操作对象也进上下文：确认前记下目标对象，下一轮「再给它延长一次」可直接定位
                        updateContextEntities(conversationId, tenantId, userId, node.path("entities"));
                        sendEvent(emitter, json, clientDisconnected);
                    }
                    // tool_call/tool_result：工具执行过程事件，原样透传前端；
                    // 同时配对缓存/落库，写 ai_tool_calls 审计（独立于 tool_calls_json 回显）
                    case "tool_call" -> {
                        pendingToolCall = new ToolCallAudit(
                                node.path("tool").asText(""), node.path("arguments").toString());
                        sendEvent(emitter, json, clientDisconnected);
                    }
                    case "tool_result" -> {
                        persistToolCallAudit(conversationId, assistantMessageId, tenantId, userId,
                                pendingToolCall, node);
                        updateContextEntities(conversationId, tenantId, userId, node.path("entities"));
                        // 累积回显轨迹（前端历史展示「AI 查了什么」）
                        toolCalls.add(buildToolCallTrace(pendingToolCall, node));
                        pendingToolCall = null;
                        sendEvent(emitter, json, clientDisconnected);
                    }
                    case "error" -> throw new BizException(ErrorCode.AI_SERVICE_UNAVAILABLE);
                    default -> log.debug("忽略未知 SSE 事件类型: {}", type);
                }
                if (clientDisconnected.get()) {
                    break;
                }
            }
        }
        return new StreamResult(citationsJson, serializeToolCalls(toolCalls));
    }

    /**
     * 组装单条工具调用回显轨迹：工具名 + 参数 + 成败 + 摘要（供前端展示）。
     *
     * @param call      配对的 tool_call 事件（含参数），可能为 null
     * @param resultNode tool_result 事件
     * @return 可序列化的轨迹 Map
     */
    private Map<String, Object> buildToolCallTrace(ToolCallAudit call, JsonNode resultNode) {
        Map<String, Object> trace = new LinkedHashMap<>();
        String toolName = resultNode.path("tool").asText("");
        if (toolName.isEmpty() && call != null) {
            toolName = call.toolName();
        }
        trace.put("tool", toolName);
        trace.put("success", resultNode.path("success").asBoolean(false));
        trace.put("summary", resultNode.path("summary").asText(""));
        trace.put("costMs", resultNode.path("costMs").asInt(0));
        if (call != null && call.argumentsJson() != null) {
            try {
                // 参数原样透传为 JSON 对象（前端按需展示），解析失败则存原始字符串
                trace.put("arguments", objectMapper.readTree(call.argumentsJson()));
            } catch (Exception e) {
                trace.put("arguments", call.argumentsJson());
            }
        }
        return trace;
    }

    /**
     * 把工具结果中的业务对象**合并**进会话上下文，供后续追问做指代消解。
     * <p>
     * 合并规则（LRU）：按 (objectType, objectId) 去重，本次命中的对象移到队首，超上限丢队尾。
     * <p>
     * 为什么不能整列覆盖：同一轮里后一个工具会把前一个的结果冲掉——先 list_contracts 再查交付单，
     * 合同上下文就没了；跨轮也无法累积。用户说「第 3 条」时可能定位不到对象。
     */
    void updateContextEntities(Long conversationId, Long tenantId, Long userId, JsonNode entitiesNode) {
        if (entitiesNode == null || !entitiesNode.isArray() || entitiesNode.isEmpty()) {
            return;
        }
        // 1. 本次工具结果里的对象（只取下游真正消费的三项）
        List<Map<String, Object>> incoming = new ArrayList<>();
        for (JsonNode item : entitiesNode) {
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("objectType", item.path("objectType").asText(""));
            entity.put("objectId", item.path("objectId").asText(""));
            entity.put("title", item.path("title").asText(""));
            if (!String.valueOf(entity.get("objectId")).isBlank()) {
                // 记录写入时刻：超 TTL 的实体在下次合并时被丢弃
                entity.put(ENTITY_AT_FIELD, System.currentTimeMillis());
                incoming.add(entity);
            }
        }
        if (incoming.isEmpty()) {
            return;
        }
        try {
            // 2. 读现有上下文，与本次结果 LRU 合并后整体写回
            List<Map<String, Object>> merged = mergeContextEntities(
                    readContextEntities(conversationId, tenantId, userId), incoming);
            String json = objectMapper.writeValueAsString(merged);
            conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .eq(AiConversation::getTenantId, tenantId)
                    .eq(AiConversation::getUserId, userId)
                    .set(AiConversation::getContextEntities, json));
        } catch (Exception e) {
            log.warn("更新会话上下文失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    /** 读会话当前的上下文实体列表；无内容或解析失败一律返回空列表（不阻断主流程） */
    private List<Map<String, Object>> readContextEntities(Long conversationId, Long tenantId, Long userId) {
        AiConversation conversation = conversationMapper.selectByIdAndTenantUser(conversationId, tenantId, userId);
        if (conversation == null || conversation.getContextEntities() == null
                || conversation.getContextEntities().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(conversation.getContextEntities(),
                    new TypeReference<List<Map<String, Object>>>() { });
        } catch (Exception e) {
            log.warn("会话上下文解析失败，按空处理: conversationId={}, error={}", conversationId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * LRU 合并：本次的排最前，其后是旧列表里未过期的（保持原有相对顺序），按 (objectType, objectId) 去重。
     *
     * @param existing 会话里已有的实体（越靠前越新）
     * @param incoming 本次工具结果产出的实体（越靠前越新）
     * @return 合并后的实体列表，长度不超过 {@link #MAX_CONTEXT_ENTITIES}
     */
    List<Map<String, Object>> mergeContextEntities(List<Map<String, Object>> existing,
                                                   List<Map<String, Object>> incoming) {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // 本次工具结果一定是最新的，直接入列
        addDistinct(merged, seen, incoming);
        // 旧实体：超过 TTL 的丢弃（避免隔天残留的旧对象误导代词消解），其余按原顺序补在后面
        for (Map<String, Object> entity : existing) {
            if (!isExpired(entity, now)) {
                addDistinct(merged, seen, List.of(entity));
            }
        }
        return merged;
    }

    /**
     * 实体是否已过期。
     * <p>没有 at 字段的一律视为过期——老数据或外部写入的实体无法判断新旧，宁可丢弃也不误用。
     */
    private boolean isExpired(Map<String, Object> entity, long now) {
        Object at = entity.get(ENTITY_AT_FIELD);
        return !(at instanceof Number) || now - ((Number) at).longValue() > CONTEXT_ENTITY_TTL_MILLIS;
    }

    /** 按 (objectType, objectId) 去重追加，达到上限即停 */
    private void addDistinct(List<Map<String, Object>> target, Set<String> seen, List<Map<String, Object>> source) {
        for (Map<String, Object> entity : source) {
            if (target.size() >= MAX_CONTEXT_ENTITIES) {
                return;
            }
            if (seen.add(entity.get("objectType") + "#" + entity.get("objectId"))) {
                target.add(entity);
            }
        }
    }

    /**
     * 序列化工具调用轨迹为 JSON 字符串（空则返回 null，避免存空数组）。
     */
    private String serializeToolCalls(List<Map<String, Object>> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception e) {
            log.warn("工具轨迹序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式结果容器：引用来源 + 工具调用轨迹，一并回传给收尾落库。
     *
     * @param citationsJson 引用来源 JSON（无则 null）
     * @param toolCallsJson 工具调用轨迹 JSON（无则 null）
     */
    private record StreamResult(String citationsJson, String toolCallsJson) {
    }

    /**
     * 落一条工具调用审计（ai_tool_calls）。
     * <p>
     * 审计失败（如 DB 抖动）只记 warn，不抛出——审计是旁路，不能拖垮主对话流。
     * 参数取配对的 tool_call 事件（tool_result 事件不含参数），工具名优先用 result 事件的，
     * 缺失再退回缓存的 call 事件。
     *
     * @param conversationId     会话ID
     * @param assistantMessageId 本轮 assistant 消息ID
     * @param tenantId           租户ID
     * @param userId             用户ID
     * @param call              配对的 tool_call 事件（可能为 null，如上游漏发）
     * @param resultNode         tool_result 事件 JSON
     */
    private void persistToolCallAudit(Long conversationId, Long assistantMessageId, Long tenantId, Long userId,
                                      ToolCallAudit call, JsonNode resultNode) {
        String toolName = resultNode.path("tool").asText("");
        if (toolName.isEmpty() && call != null) {
            toolName = call.toolName();
        }
        String argumentsJson = call != null ? call.argumentsJson() : null;
        boolean success = resultNode.path("success").asBoolean(false);
        String error = resultNode.path("error").isMissingNode() || resultNode.path("error").isNull()
                ? null : resultNode.path("error").asText(null);
        int costMs = resultNode.path("costMs").asInt(0);
        try {
            toolCallMapper.insert(AiToolCall.builder()
                    .conversationId(conversationId)
                    .messageId(assistantMessageId)
                    .tenantId(tenantId)
                    .userId(userId)
                    .toolName(toolName)
                    .argumentsJson(argumentsJson)
                    .status(success ? "success" : "failed")
                    .errorMessage(error)
                    .costTimeMs(costMs)
                    .build());
        } catch (Exception e) {
            log.warn("工具调用审计落库失败（不阻断对话）: tool={}, error={}", toolName, e.getMessage());
        }
    }

    /**
     * 向浏览器推一条 SSE。连接已断则标记中断，不再抛给主流程。
     *
     * @param emitter            SSE 输出
     * @param json               data 行 JSON
     * @param clientDisconnected 断开标记
     */
    private void sendEvent(SseEmitter emitter, String json, AtomicBoolean clientDisconnected) {
        if (clientDisconnected.get()) {
            return;
        }
        try {
            emitter.send(json);
        } catch (IOException e) {
            clientDisconnected.set(true);
            log.info("SSE 推送失败，视为客户端已断开: {}", e.getMessage());
        }
    }

    /**
     * 工具调用审计的配对缓存：tool_call 事件到达时暂存，等 tool_result 配对落库。
     *
     * @param toolName       工具名
     * @param argumentsJson  模型生成的参数（JSON 字符串）
     */
    private record ToolCallAudit(String toolName, String argumentsJson) {
    }
}
