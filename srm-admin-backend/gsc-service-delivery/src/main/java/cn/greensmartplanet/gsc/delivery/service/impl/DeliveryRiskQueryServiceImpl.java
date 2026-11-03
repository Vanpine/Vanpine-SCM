package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryRisk;
import cn.greensmartplanet.gsc.delivery.enums.RiskStatus;
import cn.greensmartplanet.gsc.delivery.mapper.Delivery8dReportMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryLogisticsEventMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskFactorMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryRiskMapper;
import cn.greensmartplanet.gsc.common.enums.MatchReasonStatus;
import cn.greensmartplanet.gsc.common.event.WeatherAdvicePayload;
import cn.greensmartplanet.gsc.delivery.enums.RiskType;
import cn.greensmartplanet.gsc.delivery.mq.producer.WeatherAdvicePublisher;
import cn.greensmartplanet.gsc.delivery.service.DeliveryRiskQueryService;
import cn.greensmartplanet.gsc.delivery.service.WeatherAdviceService;
import cn.greensmartplanet.gsc.delivery.support.WeatherAdviceCache;
import cn.greensmartplanet.gsc.delivery.vo.DeliveryRiskDashboardVO;
import cn.greensmartplanet.gsc.delivery.vo.DeliveryRiskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交付风险查询服务实现
 * <p>
 * 纯读：风险分页 / 详情 / 按交付单取未关闭风险。合同归交易域、画像归供应商域，Feign 批量回填。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryRiskQueryServiceImpl implements DeliveryRiskQueryService {

    private final DeliveryRiskMapper riskMapper;
    private final DeliveryRiskFactorMapper factorMapper;
    private final DeliveryLogisticsEventMapper eventMapper;
    private final DeliveryMapper deliveryMapper;
    private final Delivery8dReportMapper report8dMapper;
    // 合同规则字段归交易域，Feign 批量查询（列表/详情回填）
    private final ContractClient contractClient;
    // 画像归供应商域，Feign 批量查询（企业名回填）
    private final ProfileClient profileClient;
    private final WeatherAdviceService weatherAdviceService;
    private final WeatherAdvicePublisher weatherAdvicePublisher;

    /** 分页查风险（采购/供应双视角），批量回填合同/企业/交付单号 */
    @Override
    public Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                                    String riskStatus, String riskLevel, String role) {
        int offset = (pageNum - 1) * pageSize;
        List<DeliveryRisk> records;
        long total;
        if ("supplier".equals(role)) {
            records = riskMapper.selectBySupplier(tenantId, riskStatus, riskLevel, offset, pageSize);
            total = riskMapper.countBySupplier(tenantId, riskStatus, riskLevel);
        } else {
            records = riskMapper.selectByBuyer(tenantId, riskStatus, riskLevel, offset, pageSize);
            total = riskMapper.countByBuyer(tenantId, riskStatus, riskLevel);
        }

        List<DeliveryRiskVO> vos = fillVOList(records);

        Map<String, Object> result = new HashMap<>();
        result.put("records", vos);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 驾驶舱分页：按交付单聚合活跃风险（一行一交付单），等级取最高、分取最高、类型标签组 */
    @Override
    public Map<String, Object> listDashboard(Long tenantId, int pageNum, int pageSize,
                                             String riskStatus, String riskLevel, String role) {
        int offset = (pageNum - 1) * pageSize;
        List<DeliveryRiskDashboardVO> records;
        long total;
        if ("supplier".equals(role)) {
            records = riskMapper.selectDashboardBySupplier(tenantId, riskStatus, riskLevel, offset, pageSize);
            total = riskMapper.countDashboardBySupplier(tenantId, riskStatus, riskLevel);
        } else {
            records = riskMapper.selectDashboardByBuyer(tenantId, riskStatus, riskLevel, offset, pageSize);
            total = riskMapper.countDashboardByBuyer(tenantId, riskStatus, riskLevel);
        }
        // 拆分 risk_types_raw → riskTypes 列表，并回填企业名（交付单号/合同标题 SQL 已回填）
        fillDashboardCompany(records);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 风险详情：回填合同/企业/交付单信息 + 物流事件时间线 */
    @Override
    public Map<String, Object> detail(Long id, Long tenantId) {
        DeliveryRisk r = getRisk(id, tenantId);
        DeliveryRiskVO vo = fillVOList(List.of(r)).get(0);
        vo.setEvents(eventMapper.selectByDeliveryId(r.getDeliveryId()));
        vo.setFactors(factorMapper.selectByRiskId(r.getId()));
        attachWeatherAdvice(vo, r);
        Map<String, Object> result = new HashMap<>();
        result.put("risk", vo);
        // 附带 8D 整改报告（一个风险一张，无则 null）
        result.put("report8d", report8dMapper.selectByRiskId(r.getId()));
        return result;
    }

    /** 按交付单查未关闭风险（物流追踪页风险联动）：无未关闭风险返回 data=null */
    @Override
    public Map<String, Object> getActiveByDelivery(Long deliveryId, Long tenantId) {
        // 1. 校验交付单归属（采购方或供应方），复用 getRisk 的归属校验思路
        Delivery d = deliveryMapper.selectById(deliveryId);
        if (d == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        // 2. 取未关闭风险（未关闭含 active/warned/high/disposing，mitigated/closed 排除）
        List<DeliveryRisk> active = riskMapper.selectActiveByDelivery(deliveryId);
        List<DeliveryRisk> open = active.stream()
                .filter(r -> !RiskStatus.MITIGATED.getCode().equals(r.getRiskStatus())
                        && !RiskStatus.CLOSED.getCode().equals(r.getRiskStatus()))
                .toList();
        if (open.isEmpty()) {
            // Map.of 不允许 null 值，用 HashMap 承载 risk=null（约定：无未关闭风险）
            Map<String, Object> empty = new HashMap<>();
            empty.put("risk", null);
            return empty;
        }
        // 3. 多个取最新（防御：正常一单只应有一个未关闭风险），回填合同/企业/交付单号
        DeliveryRisk latest = open.stream()
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .findFirst().orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("risk", fillVOList(List.of(latest)).get(0));
        return result;
    }

    // ==================== 私有工具 ====================

    /** 驾驶舱聚合行补企业名（Feign 批量查画像），并拆分风险类型列表 */
    private void fillDashboardCompany(List<DeliveryRiskDashboardVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 1. 风险类型：risk_types_raw（逗号串）→ riskTypes 列表
        records.forEach(vo -> {
            String raw = vo.getRiskTypesRaw();
            if (raw != null && !raw.isBlank()) {
                vo.setRiskTypes(Arrays.stream(raw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .collect(Collectors.toList()));
            } else {
                vo.setRiskTypes(Collections.emptyList());
            }
        });
        // 2. 企业名：需要交付单号定位，先用 selectBatchIds 查交付单拿租户 ID
        Set<Long> deliveryIds = records.stream().map(DeliveryRiskDashboardVO::getDeliveryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Delivery> deliveryMap = deliveryIds.isEmpty() ? Map.of()
                : deliveryMapper.selectBatchIds(deliveryIds).stream()
                .collect(Collectors.toMap(Delivery::getId, x -> x));
        Set<Long> tenantIds = new HashSet<>();
        deliveryMap.values().forEach(dd -> {
            tenantIds.add(dd.getBuyerTenantId());
            tenantIds.add(dd.getSupplierTenantId());
        });
        Map<Long, String> companyMap = tenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(tenantIds)).getData().values().stream()
                .collect(Collectors.toMap(ProfileInfoDTO::getTenantId, ProfileInfoDTO::getCompanyName));
        records.forEach(vo -> {
            Delivery dd = deliveryMap.get(vo.getDeliveryId());
            if (dd != null) {
                vo.setBuyerCompanyName(companyMap.get(dd.getBuyerTenantId()));
                vo.setSupplierCompanyName(companyMap.get(dd.getSupplierTenantId()));
            }
        });
    }

    /** 查风险并校验归属（采购方或供应方）——查询侧私有副本（读），写侧归属校验在 DeliveryRiskLifecycleService */
    private DeliveryRisk getRisk(Long id, Long tenantId) {
        DeliveryRisk r = riskMapper.selectById(id);
        if (r == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        Delivery d = deliveryMapper.selectById(r.getDeliveryId());
        if (d == null) {
            throw new BizException(ErrorCode.RISK_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return r;
    }

    /** 批量回填合同/企业/交付单号（合同归交易域、画像归供应商域，均 Feign 批量查询） */
    private List<DeliveryRiskVO> fillVOList(List<DeliveryRisk> records) {
        Set<Long> contractIds = records.stream().map(DeliveryRisk::getContractId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ContractInfoDTO> contractMap = contractIds.isEmpty() ? Map.of()
                : contractClient.getContractByIds(new ArrayList<>(contractIds)).getData();

        Set<Long> deliveryIds = records.stream().map(DeliveryRisk::getDeliveryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Delivery> deliveryMap = deliveryIds.isEmpty() ? Map.of()
                : deliveryMapper.selectBatchIds(deliveryIds).stream()
                .collect(Collectors.toMap(Delivery::getId, x -> x));

        Set<Long> tenantIds = new HashSet<>();
        deliveryMap.values().forEach(dd -> {
            tenantIds.add(dd.getBuyerTenantId());
            tenantIds.add(dd.getSupplierTenantId());
        });
        Map<Long, String> companyMap = tenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(tenantIds)).getData().values().stream()
                .collect(Collectors.toMap(ProfileInfoDTO::getTenantId, ProfileInfoDTO::getCompanyName));

        return records.stream().map(r -> {
            DeliveryRiskVO vo = DeliveryRiskVO.fromEntity(r);
            ContractInfoDTO c = contractMap.get(r.getContractId());
            Delivery dd = deliveryMap.get(r.getDeliveryId());
            if (c != null) {
                vo.setContractNo(c.getContractNo());
                vo.setContractTitle(c.getTitle());
            }
            if (dd != null) {
                vo.setDeliveryNo(dd.getDeliveryNo());
                vo.setBuyerCompanyName(companyMap.get(dd.getBuyerTenantId()));
                vo.setSupplierCompanyName(companyMap.get(dd.getSupplierTenantId()));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /** 天气风险才挂 AI 建议；缓存没有则补发一次任务，详情先出 generating */
    private void attachWeatherAdvice(DeliveryRiskVO vo, DeliveryRisk r) {
        if (!RiskType.WEATHER_DISRUPTION.getCode().equals(r.getRiskType())) {
            return;
        }
        WeatherAdviceCache cache = weatherAdviceService.read(r.getId());
        if (cache == null) {
            Delivery d = deliveryMapper.selectById(r.getDeliveryId());
            if (d != null) {
                weatherAdvicePublisher.publish(WeatherAdvicePayload.builder()
                        .riskId(r.getId())
                        .deliveryId(d.getId())
                        .deliveryNo(d.getDeliveryNo())
                        .place(d.getDeliveryPlace())
                        .evidence(r.getEvidence())
                        .summary(r.getEvidence())
                        .level(r.getRiskLevel())
                        .shipCompany(d.getShipCompany())
                        .deliveryPlace(d.getDeliveryPlace())
                        .eta(r.getEstimatedArrivalTime() == null ? null : r.getEstimatedArrivalTime().toString())
                        .promisedDate(r.getPromisedDeliveryDate() == null ? null : r.getPromisedDeliveryDate().toString())
                        .impactQty(r.getImpactQty())
                        .build());
            }
            vo.setAdviceStatus(MatchReasonStatus.GENERATING.getCode());
            vo.setAdviceLines(Collections.emptyList());
            return;
        }
        vo.setAdviceStatus(cache.getStatus());
        vo.setAdviceLines(cache.getLines() == null ? Collections.emptyList() : cache.getLines());
    }
}
