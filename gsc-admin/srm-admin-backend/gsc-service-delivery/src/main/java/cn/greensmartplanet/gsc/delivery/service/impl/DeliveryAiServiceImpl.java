package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryDetailDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryLogisticsEventDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryOperateRequest;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryPromiseDateDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryQuery;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliverySummaryDTO;
import cn.greensmartplanet.gsc.api.delivery.dto.AiDeliveryTraceDTO;
import cn.greensmartplanet.gsc.delivery.dto.PromiseDateDTO;
import cn.greensmartplanet.gsc.delivery.dto.ReturnLogisticsDTO;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryLogisticsEvent;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryPromiseDate;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryTrace;
import cn.greensmartplanet.gsc.delivery.enums.DeliveryStatus;
import cn.greensmartplanet.gsc.delivery.service.DeliveryAiService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryPromiseDateService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryQueryService;
import cn.greensmartplanet.gsc.delivery.service.DeliveryReturnService;
import cn.greensmartplanet.gsc.delivery.vo.DeliveryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 交付 AI 适配服务实现 — AI DTO 组装 + 状态中文 + 业务 DTO 转换
 * <p>
 * 读路径复用 {@link DeliveryQueryService} 的 list/getDetail（返回 Map），
 * 这里把 Map 里的 VO 转成契约 DTO 并填充 statusText；写路径委托已有业务方法。
 * 承诺原因模型可能不传，适配层兜底默认文案（PromiseDateDTO.reason 必填）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAiServiceImpl implements DeliveryAiService {

    private final DeliveryQueryService deliveryQueryService;
    private final DeliveryPromiseDateService promiseDateService;
    private final DeliveryReturnService returnService;

    /** 承诺原因缺省文案（模型未推断出具体原因时兜底） */
    private static final String DEFAULT_PROMISE_REASON = "供应商通过 AI 提交承诺交期";

    @Override
    public List<AiDeliverySummaryDTO> listForAi(AiDeliveryQuery query) {
        Map<String, Object> result = deliveryQueryService.list(
                query.getTenantId(),
                pageOf(query), pageSizeOf(query),
                query.getStatus(), query.getKeyword(), query.getRole());
        @SuppressWarnings("unchecked")
        List<DeliveryVO> records = (List<DeliveryVO>) result.get("records");
        if (records == null) {
            return new ArrayList<>();
        }
        return records.stream().map(this::toSummary).toList();
    }

    @Override
    public AiDeliveryDetailDTO getForAi(AiDeliveryQuery query) {
        Map<String, Object> result = deliveryQueryService.getDetail(query.getDeliveryId(), query.getTenantId());
        DeliveryVO vo = (DeliveryVO) result.get("delivery");
        if (vo == null) {
            return null;
        }
        AiDeliveryDetailDTO dto = new AiDeliveryDetailDTO();
        fillSummary(dto, vo);
        @SuppressWarnings("unchecked")
        List<DeliveryTrace> traces = (List<DeliveryTrace>) result.get("traces");
        @SuppressWarnings("unchecked")
        List<DeliveryLogisticsEvent> events = (List<DeliveryLogisticsEvent>) result.get("logisticsEvents");
        @SuppressWarnings("unchecked")
        List<DeliveryPromiseDate> promises = (List<DeliveryPromiseDate>) result.get("promiseDates");
        dto.setTraces(traces == null ? new ArrayList<>() : traces.stream().map(t -> AiDeliveryTraceDTO.builder()
                .action(t.getAction())
                .actionDesc(t.getActionDesc())
                .operatorTenantId(t.getOperatorTenantId())
                .remark(t.getRemark())
                .createTime(t.getCreateTime())
                .build()).toList());
        dto.setLogisticsEvents(events == null ? new ArrayList<>() : events.stream().map(e -> AiDeliveryLogisticsEventDTO.builder()
                .eventType(e.getEventType())
                .eventTime(e.getEventTime())
                .estimatedArrivalTime(e.getEstimatedArrivalTime())
                .location(e.getLocation())
                .remark(e.getRemark())
                .source(e.getSource())
                .build()).toList());
        dto.setPromiseDates(promises == null ? new ArrayList<>() : promises.stream().map(p -> AiDeliveryPromiseDateDTO.builder()
                .id(p.getId())
                .promisedDate(p.getPromisedDate())
                .reason(p.getReason())
                .status(p.getStatus())
                .createTime(p.getCreateTime())
                .build()).toList());
        dto.setReturnReason(vo.getReturnReason());
        dto.setReturnShipCompany(vo.getReturnShipCompany());
        dto.setReturnShipTrackingNo(vo.getReturnShipTrackingNo());
        dto.setDefectiveQty(vo.getDefectiveQty());
        dto.setReshipFrom(vo.getReshipFrom());
        dto.setAcceptRemark(vo.getAcceptRemark());
        dto.setAcceptTime(vo.getAcceptTime());
        return dto;
    }

    @Override
    public Long submitPromiseDate(AiDeliveryOperateRequest request) {
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? DEFAULT_PROMISE_REASON : request.getReason();
        PromiseDateDTO dto = new PromiseDateDTO();
        dto.setPromisedDate(request.getPromisedDate());
        dto.setReason(reason);
        return promiseDateService.submitPromiseDate(
                request.getDeliveryId(), request.getTenantId(), request.getUserId(), dto);
    }

    @Override
    public void fillReturnLogistics(AiDeliveryOperateRequest request) {
        ReturnLogisticsDTO dto = new ReturnLogisticsDTO();
        dto.setReturnShipCompany(request.getLogisticsCompany());
        dto.setReturnShipTrackingNo(request.getTrackingNo());
        returnService.fillReturnLogistics(
                request.getDeliveryId(), request.getTenantId(), request.getUserId(), dto);
    }

    /** VO → 摘要 DTO（含 statusText 中文填充） */
    private AiDeliverySummaryDTO toSummary(DeliveryVO vo) {
        AiDeliverySummaryDTO dto = new AiDeliverySummaryDTO();
        fillSummary(dto, vo);
        return dto;
    }

    /** 摘要字段填充（list/detail 共用），status 保留英文、statusText 填中文 */
    private void fillSummary(AiDeliverySummaryDTO dto, DeliveryVO vo) {
        dto.setId(vo.getId());
        dto.setDeliveryNo(vo.getDeliveryNo());
        dto.setContractId(vo.getContractId());
        dto.setContractNo(vo.getContractNo());
        dto.setContractTitle(vo.getContractTitle());
        dto.setInquiryNo(vo.getInquiryNo());
        dto.setBuyerTenantId(vo.getBuyerTenantId());
        dto.setBuyerCompanyName(vo.getBuyerCompanyName());
        dto.setSupplierTenantId(vo.getSupplierTenantId());
        dto.setSupplierCompanyName(vo.getSupplierCompanyName());
        dto.setStatus(vo.getStatus());
        dto.setStatusText(DeliveryStatus.descOf(vo.getStatus()));
        dto.setQuantity(vo.getQuantity());
        dto.setShipCompany(vo.getShipCompany());
        dto.setShipTrackingNo(vo.getShipTrackingNo());
        dto.setDeliveryDate(vo.getDeliveryDate());
        dto.setConfirmedDeliveryDate(vo.getConfirmedDeliveryDate());
        dto.setShipDate(vo.getShipDate());
        dto.setReceiveTime(vo.getReceiveTime());
        dto.setAcceptResult(vo.getAcceptResult());
        dto.setLatestLogisticsEvent(vo.getLatestLogisticsEvent());
        dto.setLatestEta(vo.getLatestEta());
        dto.setPendingPromiseId(vo.getPendingPromiseId());
        dto.setPendingPromisedDate(vo.getPendingPromisedDate());
        dto.setCreateTime(vo.getCreateTime());
    }

    private int pageOf(AiDeliveryQuery query) {
        return query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    }

    private int pageSizeOf(AiDeliveryQuery query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }
}
