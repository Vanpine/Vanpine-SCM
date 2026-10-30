package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.ImageDescriptionDTO;
import cn.greensmartplanet.gsc.ai.entity.AiConversation;
import cn.greensmartplanet.gsc.ai.entity.AiMessage;
import cn.greensmartplanet.gsc.ai.mapper.AiConversationMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageMapper;
import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文压缩 — 把滑动窗口外的老对话压成摘要，写回 ai_conversations.summary
 * <p>
 * 由 AiChatServiceImpl 在历史超阈值时异步触发。压缩是**旁路**：失败只记日志、保留旧摘要，
 * 绝不影响主对话流程（下一轮还有机会再压）。
 * <p>
 * 增量压缩：输入「旧摘要 + 新变老的消息」，输出一份新摘要，避免每次从头压。
 * <p>
 * 独立成 Bean 而非塞进 AiChatServiceImpl：@Async 同类自调用不走代理，会退化成同步执行。
 * <p>
 * 注：按 CLAUDE.md §7.0 异步选型，压缩属「丢了可接受 + 下一轮会再压」的旁路增强，
 * 因此用 @Async 而非 MQ——为一条轻量任务引入 producer/consumer/拓扑三件套不划算。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextCompactService {

    /** 单次最多压多少条，防提示词过大；剩下的下一轮再压（同时是 SQL 的 LIMIT） */
    private static final int MAX_BATCH = 40;
    /**
     * 单批压缩消息的 token 上限（估算值）。
     * <p>
     * 条数上限管不住体积：40 条 2000 token 的长回答 = 8 万 token 的压缩 prompt。
     * 8000 约等于 8000 汉字，配合 800 字输出摘要，输入输出都在合理量级。
     */
    static final int MAX_BATCH_TOKENS = 8000;
    /**
     * 摘要字符硬上限，与 Python {@code COMPACT_HARD_CAP} 一致（提示词目标为 600）。
     * <p>
     * Python 已在返回前截断，这里再兜一次：摘要每轮随历史送模型，
     * 上游若换实现或绕过截断，落库的超长摘要会把上下文预算吃穿。
     */
    private static final int MAX_SUMMARY_CHARS = 800;
    /**
     * 至少积累这么多条才值得压一次（成本闸门之一）。
     * <p>
     * 窗口满后每轮都会掉出消息，但压缩要花一次大模型调用——
     * 攒够一批再压，避免每轮都压（约每 6 轮一次）。这是成本闸门，不是正确性要求。
     */
    private static final int MIN_BATCH = 6;
    /**
     * 待压区内容达到这么多 token 就必须压（成本闸门的另一条，与 {@link #MIN_BATCH} 是「或」关系）。
     * <p>
     * 只看条数有盲区：3 条 800 token 的长回答 = 2400 token 不可见，而 6 条 50 token 的
     * 寒暄才 300 token。前者该立刻压，后者等得起。
     * <p>
     * 取 2000 的依据：滑动窗口预算是 3000 token，待压区积到 2/3 个窗口的量时，
     * 模型缺失的信息已经比它看到的历史还多，再等下去失忆会盖过收益。
     */
    private static final int MIN_BATCH_TOKENS = 2000;
    /** 连接超时（Python 服务不可用快速失败） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 读超时（压缩是一次非流式大模型调用，比对话慢，放宽到 60s） */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    /**
     * 压缩锁 key 前缀：同一会话同时只允许一个实例压缩。
     * <p>
     * CAS 写回只保证结果不互相覆盖；不加锁时，多个实例（或多轮同时溢出）会各自取同一批
     * 老消息、各调一次大模型，一个成功其余丢弃——白烧 token。
     */
    private static final String COMPACT_LOCK_PREFIX = "ai:compact:lock:";

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AiChatImageDescService imageDescService;

    /** Python AI 服务地址，由 Nacos / application-dev.yml 注入，禁止代码内写死 */
    @Value("${gsc.ai.service.url}")
    private String aiServiceUrl;

    /** Python AI 服务鉴权 token（X-API-Key） */
    @Value("${gsc.ai.service.token}")
    private String aiServiceToken;

    /**
     * 异步压缩会话上下文：老消息 → 摘要，覆盖写回会话。
     *
     * @param conversationId       会话ID
     * @param tenantId             租户ID（归属校验，防跨租户压缩）
     * @param windowStartMessageId 本轮滑动窗口里最早那条消息的 ID。窗口内的原文不参与压缩——
     *                             否则同一条消息会既作为原文喂给模型、又已压进摘要，上下文重复。
     *                             为 null（窗口为空）时不设上界
     */
    @Async
    public void compactAsync(Long conversationId, Long tenantId, Long windowStartMessageId) {
        String lockKey = COMPACT_LOCK_PREFIX + conversationId;
        try {
            // 0. 抢会话级压缩锁：多实例 / 多轮同时溢出时只让一个进去压
            if (!tryLock(lockKey)) {
                log.info("压缩跳过：另一实例正在压缩该会话 conversationId={}", conversationId);
                return;
            }
            doCompact(conversationId, tenantId, windowStartMessageId);
        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * 抢压缩锁。Redis 异常时降级为「继续压」——压缩是旁路增强，
     * 因缓存故障放弃压缩会让摘要永远落后于窗口，宁可多烧一次 token。
     */
    private boolean tryLock(String lockKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(SystemConstants.AI_COMPACT_LOCK_TTL)));
        } catch (Exception e) {
            log.warn("压缩锁获取异常，降级为继续压缩: key={}, error={}", lockKey, e.getMessage());
            return true;
        }
    }

    /** 释放压缩锁；失败不抛（TTL 兜底），避免影响主流程。 */
    private void releaseLock(String lockKey) {
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("压缩锁释放异常（TTL 兜底）: key={}, error={}", lockKey, e.getMessage());
        }
    }

    /**
     * 实际压缩流程（持锁后执行）。
     *
     * @param conversationId       会话ID
     * @param tenantId             租户ID
     * @param windowStartMessageId 窗口起点（压缩上界）
     */
    private void doCompact(Long conversationId, Long tenantId, Long windowStartMessageId) {
        try {
            // 1. 读会话：拿现有摘要与「摘要已覆盖到哪条」
            AiConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !tenantId.equals(conversation.getTenantId())) {
                log.warn("压缩跳过：会话不存在或租户不匹配 conversationId={}", conversationId);
                return;
            }
            log.info("压缩开始: conversationId={}, 旧水位线={}, 窗口起点={}",
                    conversationId, conversation.getSummaryUpToMsgId(), windowStartMessageId);
            // 2. 取「摘要覆盖点之后、滑动窗口起点之前」的老消息；没有可压的直接返回（绝大多数短会话走这里）
            List<AiMessage> messages = messageMapper.selectCompactable(
                    conversationId, tenantId, conversation.getSummaryUpToMsgId(),
                    windowStartMessageId, MAX_BATCH);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            // 2.1 按 token 截断本批：MAX_BATCH 只是条数上限，40 条长回答仍可能撑爆压缩 prompt
            int trimmedByBudget = capByTokenBudget(messages);
            if (trimmedByBudget > 0) {
                log.info("压缩批次超出 token 预算，截去尾部 {} 条（剩余下一轮再压）: conversationId={}",
                        trimmedByBudget, conversationId);
            }
            // 2.2 触发闸门（成本 vs 可见性）：条数够 或 内容够多，满足其一就压。
            //     只看条数会让「3 条长回答」长期不可见——待压区不在窗口也不在摘要，
            //     积压越多模型失忆越严重；只看 token 则会让「6 条短消息」白调一次大模型。
            int pendingTokens = messages.stream()
                    .mapToInt(m -> TokenEstimator.count(m.getContent())).sum();
            if (messages.size() < MIN_BATCH && pendingTokens < MIN_BATCH_TOKENS) {
                log.info("压缩跳过：可压 {} 条 / {} token，均未达触发线（{}条 / {}token）conversationId={}",
                        messages.size(), pendingTokens, MIN_BATCH, MIN_BATCH_TOKENS, conversationId);
                return;
            }
            // 3. 调 Python 压缩（旧摘要 + 这批老消息 → 新摘要）
            String summary = callCompact(conversation.getSummary(), messages);
            if (summary == null || summary.isBlank()) {
                log.warn("压缩返回空摘要，保留旧摘要: conversationId={}", conversationId);
                return;
            }
            // 3.1 落库前再兜一次长度上限（Python 已截断，这里防上游换实现）
            if (summary.length() > MAX_SUMMARY_CHARS) {
                log.warn("压缩返回摘要超上限，截断后落库: conversationId={}, len={}",
                        conversationId, summary.length());
                summary = summary.substring(0, MAX_SUMMARY_CHARS);
            }
            // 4. CAS 写回摘要 + 覆盖点（本批最后一条的 id）：
            //    仅当覆盖点仍是本次读到的旧值时才更新——连续两轮生成 / 多实例各自触发压缩时，
            //    后到者读到的是同一个旧水位线，不做 CAS 会互相覆盖摘要、水位线甚至回退
            int updated = conversationMapper.updateSummaryIfUnchanged(
                    conversationId, summary, messages.get(messages.size() - 1).getId(),
                    conversation.getSummaryUpToMsgId());
            if (updated == 0) {
                log.info("压缩结果已被并发的另一次压缩抢先，放弃本次写回: conversationId={}", conversationId);
                return;
            }
            log.info("会话上下文已压缩: conversationId={}, 压缩条数={}, 摘要长度={}",
                    conversationId, messages.size(), summary.length());
        } catch (Exception e) {
            // 压缩是旁路：失败保持旧摘要，下一轮还有机会再压
            log.warn("会话上下文压缩失败，保留旧摘要: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    /**
     * 按 token 预算截断本批消息（就地删除尾部）。
     * <p>
     * 条数上限（{@link #MAX_BATCH}）管不住体积：一条 2000 token 的长回答 × 40 条就是 8 万 token。
     * 从头部累加，超出预算的尾部留给下一轮（水位线保证不会漏）。
     *
     * @param messages 待压缩消息（升序，就地裁剪）
     * @return 被截去的条数
     */
    int capByTokenBudget(List<AiMessage> messages) {
        int used = 0;
        for (int i = 0; i < messages.size(); i++) {
            used += TokenEstimator.count(messages.get(i).getContent());
            if (used > MAX_BATCH_TOKENS) {
                int dropped = messages.size() - i;
                messages.subList(i, messages.size()).clear();
                return dropped;
            }
        }
        return 0;
    }

    /**
     * 调 Python /api/compact，返回新摘要。
     * <p>
     * 包级可见（非 private）便于单测打桩，避免测试真的发 HTTP。
     *
     * @param oldSummary 已有摘要（首次压缩为 null）
     * @param messages   待压缩的老消息（升序）
     * @return 新摘要；调用失败返回 null
     */
    String callCompact(String oldSummary, List<AiMessage> messages) throws Exception {
        // 1. 组装请求体：字段名与 schemas.CompactRequest 对齐（蛇形）
        //    图片描述一并带上：历史轮次不带原图，若描述不进摘要，图的信息会在掉出窗口后永久丢失
        Long tenantId = messages.isEmpty() ? null : messages.get(0).getTenantId();
        Map<Long, List<ImageDescriptionDTO>> imageDescMap = tenantId == null ? Map.of()
                : imageDescService.loadImageContext(
                        messages.stream().map(AiMessage::getId).toList(), tenantId)
                        .descriptionsByMessage();
        List<Map<String, Object>> items = new ArrayList<>();
        for (AiMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            List<ImageDescriptionDTO> imageDescs = imageDescMap.get(message.getId());
            if (imageDescs != null) {
                item.put("imageDescriptions", imageDescs);
            }
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("old_summary", oldSummary == null ? "" : oldSummary);
        body.put("messages", items);

        // 2. POST 到 Python（鉴权与 /api/chat 同一套 X-API-Key）
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/api/compact"))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-API-Key", aiServiceToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("压缩接口非 200: status={}", response.statusCode());
            return null;
        }
        // 3. 解析 { summary: "..." }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("summary").asText(null);
    }
}
