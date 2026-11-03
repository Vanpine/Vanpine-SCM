package cn.greensmartplanet.gsc.trade.controller;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryDeadlineRequest;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.common.result.R;
import cn.greensmartplanet.gsc.trade.service.InquiryLifecycleService;
import cn.greensmartplanet.gsc.trade.service.InquiryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易内部接口 — AI 工具询盘查询 + 写操作（仅服务间经内网服务发现 Feign 调用）
 * <p>
 * 网关已用 InternalBlockWebFilter 拒绝 /internal/** 外部访问（403），
 * 此处接口只对注册中心内的其他服务可见。
 * <p>
 * ⚠️ 路径一致性：本控制器路径与 gsc-api 的 AiInquiryClient 契约保持一致
 * （契约接口为调用方声明，提供方不实现该接口，两端路径由本注释约定同步修改）。
 * <p>
 * 安全边界：写操作的 tenantId/userId 由 AI 域从登录态注入（模型不可伪造），
 * 交易域据此校验归属（仅采购方自己）+ 状态前置后落库。
 *
 * @author gsc
 */
@RestController
@RequestMapping("/internal/ai/inquiry")
@RequiredArgsConstructor
public class InternalAiInquiryController {

    private final InquiryQueryService inquiryQueryService;
    private final InquiryLifecycleService inquiryLifecycleService;

    /** 按当前租户（buyer/supplier 双视角）分页查询盘摘要（AI 工具 list_inquiries；无则空列表） */
    @PostMapping("/list")
    public R<List<AiInquirySummaryDTO>> listInquiries(@RequestBody AiInquiryQuery query) {
        return R.ok(inquiryQueryService.listForAi(query));
    }

    /** 按询盘ID查单条摘要（AI 域拼确认文案用；不存在/非本人返回 data=null） */
    @PostMapping("/get")
    public R<AiInquirySummaryDTO> getInquiry(@RequestBody AiInquiryQuery query) {
        return R.ok(inquiryQueryService.getForAi(query));
    }

    /** 部分更新询盘字段（AI 工具 update_inquiry 二次确认后；写操作） */
    @PostMapping("/update")
    public R<Void> updateInquiry(@RequestBody AiInquiryUpdateRequest request) {
        inquiryLifecycleService.updateForAi(request);
        return R.ok();
    }

    /** 延长询盘报价截止（AI 工具 extend_inquiry_deadline 二次确认后；写操作） */
    @PostMapping("/extend-deadline")
    public R<Void> extendInquiryDeadline(@RequestBody AiInquiryDeadlineRequest request) {
        inquiryLifecycleService.extendDeadline(request.getInquiryId(), request.getTenantId(),
                request.getUserId(), request.getNewDeadline());
        return R.ok();
    }
}
