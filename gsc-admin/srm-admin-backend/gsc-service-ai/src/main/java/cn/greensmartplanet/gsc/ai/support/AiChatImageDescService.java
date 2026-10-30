package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.ImageDescriptionDTO;
import cn.greensmartplanet.gsc.ai.entity.AiChatImage;
import cn.greensmartplanet.gsc.ai.entity.AiMessageImage;
import cn.greensmartplanet.gsc.ai.mapper.AiChatImageMapper;
import cn.greensmartplanet.gsc.ai.mapper.AiMessageImageMapper;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图片内容描述服务 — 让历史轮次仍能指代「刚才那张图」。
 * <p>
 * 背景：图片 token 成本是同体积文字的 10 倍（一张 ≈ 800~1600 token），而历史窗口预算只有 3000，
 * 所以历史轮次一律不带原图。代价是用户追问「那张合同图里的编号」时模型失明。
 * 解法：首次把图纳入上下文时用多模态模型生成一段描述（≈100 token）落库，之后历史只带描述。
 * <p>
 * 生成策略：**按需 + 异步 + 只做一次**
 * <ul>
 *   <li>按需：不做上传时预生成——用户可能传了不用，白烧 token</li>
 *   <li>异步：生成要几秒，不能拖慢主对话；本轮用已有描述，下一轮才用上新的</li>
 *   <li>只做一次：{@code description} 为 null 才生成；空串表示「生成过但无有效内容」，不再重试</li>
 * </ul>
 * 独立成 Bean 而非塞进 AiChatImageServiceImpl：@Async 同类自调用不走代理，会退化成同步执行。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatImageDescService {

    /**
     * 单次最多生成几张，防一次会话几十张图把 Python 服务打满。
     * <p>
     * 正常路径（上传时触发）一次只有 1 张；兜底路径（按历史消息补）才可能命中上限，
     * 剩下的下一轮再补。并发度由 ForkJoinPool.commonPool 天然限制，这里只兜「总量」。
     */
    private static final int MAX_DESCRIBE_PER_RUN = 8;
    /** 连接超时（Python 服务不可用快速失败） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 读超时（多模态调用比纯文本慢，放宽到 60s） */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final AiChatImageMapper chatImageMapper;
    private final AiMessageImageMapper messageImageMapper;
    private final OssService ossService;
    private final ObjectMapper objectMapper;

    /** Python AI 服务地址，由 Nacos / application-dev.yml 注入，禁止代码内写死 */
    @Value("${gsc.ai.service.url}")
    private String aiServiceUrl;

    /** Python AI 服务鉴权 token（X-API-Key） */
    @Value("${gsc.ai.service.token}")
    private String aiServiceToken;

    /**
     * 一次查询拿到的图片上下文。
     *
     * @param messageIdsWithImages  带图的消息 ID 集合。用途：纯图片提问的消息 content 是空串
     *                              （Java 只在喂模型时才用「请解析这张图片」兜底），会被
     *                              buildHistory 的「内容非空」过滤误杀——调用方需要这个集合
     *                              把它们捞回窗口，否则图片信息在上下文里完全不存在。
     * @param descriptionsByMessage messageId → 描述列表（按上传序，带 1 起序号）；无描述的 messageId 不出现
     */
    public record ImageContext(Set<Long> messageIdsWithImages,
                               Map<Long, List<ImageDescriptionDTO>> descriptionsByMessage) {

        /** 空上下文：无图时用，免去调用方判空。 */
        public static ImageContext empty() {
            return new ImageContext(Set.of(), Map.of());
        }
    }

    /**
     * 批量读取历史消息的图片上下文（供 buildHistory / 压缩使用）。
     * <p>
     * 两条现成 SQL 组合，不新增查询：关联表拿 messageId→imageId（已按上传序排列），
     * 图片表拿描述。一条消息挂多张图时按序返回列表——用户问「第 2 张」要能对上。
     *
     * @param messageIds 历史消息 ID 列表
     * @param tenantId   租户ID（隔离过滤）
     * @return 带图消息集合 + 各消息的描述列表
     */
    public ImageContext loadImageContext(List<Long> messageIds, Long tenantId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return ImageContext.empty();
        }
        // 1. 消息 → 图片关联（SQL 已 ORDER BY message_id, id = 上传序）
        List<AiMessageImage> relations = messageImageMapper.selectByMessageIds(messageIds, tenantId);
        if (relations.isEmpty()) {
            return ImageContext.empty();
        }
        // 2. 图片 → 描述（一次批量查，不逐条查）
        List<Long> imageIds = relations.stream().map(AiMessageImage::getImageId).distinct().toList();
        Map<Long, AiChatImage> imageMap = new LinkedHashMap<>();
        for (AiChatImage img : chatImageMapper.selectByIdsAndTenant(imageIds, tenantId)) {
            imageMap.put(img.getId(), img);
        }
        // 3. 组装：序号按「该消息下第几张图」递增，与用户看到的顺序一致。
        //    描述为空也要带上条目——只发有描述的那几张会让序号断档：
        //    模型看到「附图1、附图3」却不知道附图2 存在，会把「第 2 张」答成第 1 张的内容
        //    （答错比答不出更糟）。空描述由 Python 侧渲染成「未识别出内容」占位。
        Set<Long> withImages = new LinkedHashSet<>();
        Map<Long, Integer> seqByMessage = new LinkedHashMap<>();
        Map<Long, List<ImageDescriptionDTO>> descriptions = new LinkedHashMap<>();
        for (AiMessageImage rel : relations) {
            withImages.add(rel.getMessageId());
            int seq = seqByMessage.merge(rel.getMessageId(), 1, Integer::sum);
            AiChatImage img = imageMap.get(rel.getImageId());
            if (img == null) {
                // 图片记录缺失（已被删）——无法给出描述，但序号照常占位
                continue;
            }
            descriptions.computeIfAbsent(rel.getMessageId(), k -> new ArrayList<>())
                    .add(ImageDescriptionDTO.builder()
                            .index(seq)
                            .imageId(img.getId())
                            .description(img.getDescription() == null ? "" : img.getDescription())
                            .build());
        }
        return new ImageContext(withImages, descriptions);
    }

    /**
     * 上传后立即异步生成描述（主触发点，入参是图片ID）。
     * <p>
     * 为什么在「上传时」触发而不是「图变成历史轮次时」：
     * 描述只有在图成为历史轮次后才被用上，上传时触发有整整一个来回的提前量，
     * 用户发出图片后下一轮追问「刚才那张图」时描述已就绪。
     * 按需触发（{@link #ensureAsyncByMessages}）会晚两轮才可用。
     * <p>
     * 代价是「上传了但不发送」会白烧一次多模态调用——B 端上传图片基本都会发出，可接受。
     *
     * @param imageIds 刚上传的图片 ID
     * @param tenantId 租户ID
     */
    @Async
    public void ensureAsyncForImages(List<Long> imageIds, Long tenantId) {
        generateMissing(imageIds, tenantId);
    }

    /**
     * 按历史消息异步补生成描述（兜底触发点，入参是消息ID）。
     * <p>
     * 覆盖三种上传时没生成成功的情况：上传时接口抖动、历史遗留数据、描述被清空。
     * 与上传触发不冲突——两者都只处理 {@code description == null} 的图。
     *
     * @param messageIds 历史消息 ID 列表
     * @param tenantId   租户ID
     */
    @Async
    public void ensureAsyncByMessages(List<Long> messageIds, Long tenantId) {
        try {
            if (messageIds == null || messageIds.isEmpty()) {
                return;
            }
            List<AiMessageImage> relations = messageImageMapper.selectByMessageIds(messageIds, tenantId);
            if (relations.isEmpty()) {
                return;
            }
            generateMissing(relations.stream().map(AiMessageImage::getImageId).distinct().toList(), tenantId);
        } catch (Exception e) {
            // 旁路增强：失败不影响主对话，下一轮还会再试
            log.warn("按消息补生成图片描述失败: tenantId={}, error={}", tenantId, e.getMessage());
        }
    }

    /**
     * 为「还没生成过」的图片生成描述。
     * <p>
     * 只处理 {@code description == null}：空串表示「生成过但无有效内容」，不再重试。
     *
     * @param imageIds 候选图片 ID
     * @param tenantId 租户ID
     */
    private void generateMissing(List<Long> imageIds, Long tenantId) {
        try {
            if (imageIds == null || imageIds.isEmpty()) {
                return;
            }
            List<AiChatImage> missing = chatImageMapper.selectByIdsAndTenant(imageIds, tenantId).stream()
                    .filter(img -> img.getDescription() == null)
                    .limit(MAX_DESCRIBE_PER_RUN)
                    .toList();
            if (missing.isEmpty()) {
                return;
            }
            log.info("生成图片描述: 待生成={}张, tenantId={}", missing.size(), tenantId);
            // 并行：单张要等一次多模态调用（数秒），串行 4 张就是十几秒。
            // 用 parallelStream 走 ForkJoinPool.commonPool——与 @Async 的池相互独立，
            // 不会出现「异步线程提交异步任务」的嵌套等待死锁。
            missing.parallelStream().forEach(img -> generateOne(img, tenantId));
        } catch (Exception e) {
            // 旁路增强：失败不影响主对话，下一轮还会再试
            log.warn("生成图片描述失败: tenantId={}, error={}", tenantId, e.getMessage());
        }
    }

    /**
     * 生成单张图的描述并落库。
     * <p>
     * 包级可见便于单测：要锁住的是「生成失败时写空串而非留 null」——留 null 会导致
     * 坏图每轮都被重试，白烧 token。
     *
     * @param image    图片实体
     * @param tenantId 租户ID
     * @return 实际落库的描述（失败为空串）
     */
    String generateOne(AiChatImage image, Long tenantId) {
        String description;
        try {
            String signedUrl = ossService.generateGetUrl(image.getOssKey());
            description = callDescribe(signedUrl);
        } catch (Exception e) {
            log.warn("图片描述生成失败: imageId={}, error={}", image.getId(), e.getMessage());
            // 记空串而非留 null：坏图/接口异常时不无限重试
            description = "";
        }
        if (description == null) {
            // 接口非 200 时 callDescribe 返回 null，同样按「生成过但无内容」处理
            description = "";
        }
        // 定向更新 description 一列，避免整实体 update 覆盖并发写入的其它字段
        chatImageMapper.update(null, new LambdaUpdateWrapper<AiChatImage>()
                .eq(AiChatImage::getId, image.getId())
                .eq(AiChatImage::getTenantId, tenantId)
                .set(AiChatImage::getDescription, description));
        return description;
    }

    /**
     * 调 Python /api/images/describe，返回描述。
     * <p>
     * 包级可见（非 private）便于单测打桩，避免测试真的发 HTTP。
     *
     * @param imageUrl 现签的图片 URL
     * @return 描述文本；非 200 返回 null
     */
    String callDescribe(String imageUrl) throws Exception {
        // 1. 组装请求体：字段名与 schemas.DescribeImageRequest 对齐（蛇形）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image_url", imageUrl);

        // 2. POST 到 Python（鉴权与 /api/chat、/api/compact 同一套 X-API-Key）
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiServiceUrl + "/api/images/describe"))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-API-Key", aiServiceToken)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.warn("图片描述接口非 200: status={}", response.statusCode());
            return null;
        }
        // 3. 解析 { description: "..." }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("description").asText("");
    }
}
