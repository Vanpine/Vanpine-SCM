package cn.greensmartplanet.gsc.ai.support;

import cn.greensmartplanet.gsc.ai.dto.AiToolProposal;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * AI 写操作提案辅助 — 提案的 Redis 暂存 / 归属校验消费
 * <p>
 * 写工具（如 create_supplier_note）执行时生成 proposalId 暂存待确认的操作内容，
 * 前端确认接口回传 proposalId，本组件取出并校验归属后交给落库逻辑，随后立即删除（防重放）。
 * <p>
 * 提案 TTL 10 分钟：过期未确认自动失效，用户需重新发起。
 *
 * @author gsc
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolProposalService {

    /** Redis 键前缀（提案临时态，非业务数据） */
    private static final String KEY_PREFIX = "ai:tool:proposal:";
    /** 提案有效期：10 分钟，覆盖一次对话内用户思考确认的窗口 */
    private static final Duration TTL = Duration.ofMinutes(10);
    /**
     * 执行失败放回后的有效期：2 分钟。
     * <p>
     * 只覆盖「用户看到失败提示后再点一次」的窗口；留太久会让一个已知会失败的提案长期占位。
     */
    private static final Duration RESTORE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 暂存提案并返回 proposalId（前端确认时回传）。
     */
    public String save(AiToolProposal proposal) {
        String proposalId = UUID.randomUUID().toString();
        String json;
        try {
            json = objectMapper.writeValueAsString(proposal);
        } catch (JsonProcessingException e) {
            // 提案序列化失败是编程错误，不应发生；兜底抛业务异常避免落库半成品
            throw new BizException(ErrorCode.INTERNAL_ERROR, "提案暂存失败");
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + proposalId, json, TTL);
        log.info("AI 写操作提案已暂存: proposalId={}, tool={}, tenantId={}",
                proposalId, proposal.getToolName(), proposal.getTenantId());
        return proposalId;
    }

    /**
     * 取出并消费提案（读后即删，天然防重放）：不存在/已过期/归属不符一律拒绝。
     *
     * @param proposalId 提案ID
     * @param tenantId   当前登录租户（归属校验）
     * @param userId     当前登录用户（归属校验）
     * @return 校验通过的提案
     */
    public AiToolProposal take(String proposalId, Long tenantId, Long userId) {
        if (proposalId == null || proposalId.isBlank()) {
            throw new BizException(ErrorCode.AI_TOOL_PROPOSAL_NOT_FOUND);
        }
        // 读后即删：即便并发双击，第二次拿到 null，天然幂等，避免重复落库
        String json = stringRedisTemplate.opsForValue().getAndDelete(KEY_PREFIX + proposalId);
        if (json == null) {
            throw new BizException(ErrorCode.AI_TOOL_PROPOSAL_NOT_FOUND);
        }
        AiToolProposal proposal;
        try {
            proposal = objectMapper.readValue(json, AiToolProposal.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.AI_TOOL_PROPOSAL_NOT_FOUND);
        }
        // 归属校验：提案只能由发起者本人确认（防越权操作他人提案）
        if (proposal == null || !tenantId.equals(proposal.getTenantId())
                || !userId.equals(proposal.getUserId())) {
            throw new BizException(ErrorCode.AI_TOOL_PROPOSAL_INVALID);
        }
        return proposal;
    }

    /**
     * 执行失败时把提案放回 Redis，让用户可以重试。
     * <p>
     * 为什么需要：{@link #take} 是「读后即删」，删完才调下游 Feign 落库——下游一旦失败
     * （网络抖动 / 服务重启），提案已经消失，用户再点确认只会得到「提案不存在」，
     * 而占位气泡永久停在「已发起操作，请确认执行。」。
     * <p>
     * 放回用的是较短 TTL：重试窗口只需覆盖用户看到失败提示后的一次点击。
     *
     * @param proposalId 提案ID
     * @param proposal   已取出的提案
     */
    public void restore(String proposalId, AiToolProposal proposal) {
        if (proposalId == null || proposal == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(proposal);
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + proposalId, json, RESTORE_TTL);
            log.info("写操作执行失败，提案已放回供重试: proposalId={}, tool={}",
                    proposalId, proposal.getToolName());
        } catch (Exception e) {
            // 放回失败只记日志：用户重新发起即可，不改变原本的失败语义
            log.warn("提案放回失败，用户需重新发起: proposalId={}, error={}", proposalId, e.getMessage());
        }
    }
}
