package cn.greensmartplanet.gsc.api.trade;

import cn.greensmartplanet.gsc.api.constant.ServiceNameConstants;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryDeadlineRequest;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.api.trade.factory.AiInquiryClientFallbackFactory;
import cn.greensmartplanet.gsc.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 交易域服务 Feign 客户端契约 — 专供 AI 工具调用的询盘查询 + 写操作
 * <p>
 * 契约约定（调用方与提供方共同遵守）：
 * <ul>
 *   <li>提供方 gsc-service-trade 的 InternalAiInquiryController 实现本接口，URL/参数编译期强一致</li>
 *   <li>AI 域用它替代直查/直改 inquiry 表，询盘数据由交易域统一出口</li>
 *   <li>返回类型一律契约 DTO（禁止 Map），字段由编译期保证一致</li>
 *   <li>list 无则空列表；写操作 tenantId/userId 由 AI 域从登录态注入，交易域校验归属后落库</li>
 *   <li>降级（FallbackFactory）：抛 BizException——写操作降级若静默通过会误以为已落库，
 *       查询降级会喂给模型空数据编造答案，均不静默通过</li>
 * </ul>
 *
 * @author gsc
 */
@FeignClient(
        name = ServiceNameConstants.GSC_TRADE_SERVICE,
        path = "/internal/ai/inquiry",
        contextId = "aiInquiryClient",
        fallbackFactory = AiInquiryClientFallbackFactory.class)
public interface AiInquiryClient {

    /** 按当前租户（buyer/supplier 双视角）分页查询盘摘要（无则空列表） */
    @PostMapping("/list")
    R<List<AiInquirySummaryDTO>> listInquiries(@RequestBody AiInquiryQuery query);

    /** 按询盘ID查单条摘要（AI 域拼确认文案用；不存在/非本人返回 data=null） */
    @PostMapping("/get")
    R<AiInquirySummaryDTO> getInquiry(@RequestBody AiInquiryQuery query);

    /** 部分更新询盘字段（AI 工具 update_inquiry 二次确认后；写操作） */
    @PostMapping("/update")
    R<Void> updateInquiry(@RequestBody AiInquiryUpdateRequest request);

    /** 延长询盘报价截止（AI 工具 extend_inquiry_deadline 二次确认后；写操作） */
    @PostMapping("/extend-deadline")
    R<Void> extendInquiryDeadline(@RequestBody AiInquiryDeadlineRequest request);
}
