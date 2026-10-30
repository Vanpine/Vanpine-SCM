package cn.greensmartplanet.gsc.delivery.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.trade.ContractClient;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.delivery.entity.Delivery;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryLogisticsEvent;
import cn.greensmartplanet.gsc.delivery.entity.DeliveryPromiseDate;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryLogisticsEventMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryMapper;
import cn.greensmartplanet.gsc.delivery.mapper.DeliveryPromiseDateMapper;
import cn.greensmartplanet.gsc.delivery.projection.ContractAllocationRow;
import cn.greensmartplanet.gsc.delivery.projection.CreatableContractRow;
import cn.greensmartplanet.gsc.delivery.projection.ShipmentPlanRow;
import cn.greensmartplanet.gsc.delivery.service.DeliveryQueryService;
import cn.greensmartplanet.gsc.delivery.vo.DeliveryVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 交付单查询服务实现
 * <p>
 * 纯读：分页列表 / 详情 / 可建单合同 / 发货排期 / 进行中校验。
 * 关联数据全部批量回填（合同归交易域、画像归供应商域，Feign 批量查询），无 N+1。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryQueryServiceImpl implements DeliveryQueryService {

    private final DeliveryMapper deliveryMapper;
    private final DeliveryLogisticsEventMapper logisticsEventMapper;
    private final DeliveryPromiseDateMapper promiseDateMapper;
    // 合同信息归交易域，Feign 批量查询（列表回填合同/询盘号）
    private final ContractClient contractClient;
    // 画像归供应商域，Feign 批量查询（企业名回填）
    private final ProfileClient profileClient;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /** 分页查交付单（采购/供应双视角），批量回填合同/询盘/企业名 */
    @Override
    public Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                                    String status, String keyword, String role) {
        // 1. 按角色分采购/供应视角分页查询
        int offset = (pageNum - 1) * pageSize;
        List<Delivery> records;
        long total;
        List<String> statuses = parseStatuses(status);
        if ("supplier".equals(role)) {
            records = deliveryMapper.selectBySupplier(tenantId, statuses, keyword, offset, pageSize);
            total = deliveryMapper.countBySupplier(tenantId, statuses, keyword);
        } else {
            records = deliveryMapper.selectByBuyer(tenantId, statuses, keyword, offset, pageSize);
            total = deliveryMapper.countByBuyer(tenantId, statuses, keyword);
        }

        // 批量回填合同与询盘编号（合同归交易域，Feign 批量查询）
        Set<Long> contractIds = records.stream().map(Delivery::getContractId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ContractInfoDTO> contractMap = contractIds.isEmpty() ? Map.of()
                : contractClient.getContractByIds(new ArrayList<>(contractIds)).getData();
        Map<Long, String> inquiryNoMap = contractIds.isEmpty() ? Map.of()
                : contractClient.getInquiryNosByContractIds(new ArrayList<>(contractIds)).getData();

        // 批量回填企业名（画像归供应商域，Feign 批量查询）
        Set<Long> tenantIds = new HashSet<>();
        records.forEach(d -> {
            tenantIds.add(d.getBuyerTenantId());
            tenantIds.add(d.getSupplierTenantId());
        });
        Map<Long, String> companyMap = tenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(tenantIds)).getData().values().stream()
                        .collect(Collectors.toMap(ProfileInfoDTO::getTenantId, ProfileInfoDTO::getCompanyName));

        // 批量回填最新物流事件与 ETA（物流追踪列表列：货走到哪一步/预计何时到）
        List<Long> deliveryIds = records.stream().map(Delivery::getId).toList();
        Map<Long, DeliveryLogisticsEvent> latestEventMap = deliveryIds.isEmpty() ? Map.of()
                : logisticsEventMapper.selectLatestByDeliveryIds(deliveryIds).stream()
                        .collect(Collectors.toMap(DeliveryLogisticsEvent::getDeliveryId, e -> e));
        Map<Long, DeliveryLogisticsEvent> latestEtaMap = deliveryIds.isEmpty() ? Map.of()
                : logisticsEventMapper.selectLatestEtaByDeliveryIds(deliveryIds).stream()
                        .collect(Collectors.toMap(DeliveryLogisticsEvent::getDeliveryId, e -> e));

        // 待确认承诺批量回填（采购方在物流追踪列表看到「待确认」直接进详情处理）
        Map<Long, DeliveryPromiseDate> pendingPromiseMap = deliveryIds.isEmpty() ? Map.of()
                : promiseDateMapper.selectPendingByDeliveryIds(deliveryIds).stream()
                        .collect(Collectors.toMap(DeliveryPromiseDate::getDeliveryId, p -> p));

        List<DeliveryVO> vos = records.stream().map(d -> {
            DeliveryVO vo = DeliveryVO.fromEntity(d, ossService);
            ContractInfoDTO c = contractMap.get(d.getContractId());
            if (c != null) {
                vo.setContractNo(c.getContractNo());
                vo.setContractTitle(c.getTitle());
                vo.setInquiryNo(inquiryNoMap.get(c.getId()));
            }
            vo.setBuyerCompanyName(companyMap.get(d.getBuyerTenantId()));
            vo.setSupplierCompanyName(companyMap.get(d.getSupplierTenantId()));
            DeliveryLogisticsEvent latestEvent = latestEventMap.get(d.getId());
            if (latestEvent != null) {
                vo.setLatestLogisticsEvent(latestEvent.getEventType());
            }
            DeliveryLogisticsEvent latestEta = latestEtaMap.get(d.getId());
            if (latestEta != null) {
                vo.setLatestEta(latestEta.getEstimatedArrivalTime());
            }
            DeliveryPromiseDate pending = pendingPromiseMap.get(d.getId());
            if (pending != null) {
                vo.setPendingPromiseId(pending.getId());
                vo.setPendingPromisedDate(pending.getPromisedDate());
            }
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", vos);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 交付单详情：附合同/询盘/企业名和物流轨迹 */
    @Override
    public Map<String, Object> getDetail(Long id, Long tenantId) {
        // 1. 查交付单并回填合同/询盘信息（合同归交易域，Feign 查询）
        Delivery d = getDelivery(id, tenantId);
        DeliveryVO vo = DeliveryVO.fromEntity(d, ossService);
        ContractInfoDTO c = d.getContractId() != null
                ? contractClient.getContractById(d.getContractId()).getData() : null;
        if (c != null) {
            vo.setContractNo(c.getContractNo());
            vo.setContractTitle(c.getTitle());
            if (c.getInquiryId() != null) {
                Map<Long, String> inquiryNoMap = contractClient
                        .getInquiryNosByContractIds(List.of(d.getContractId())).getData();
                vo.setInquiryNo(inquiryNoMap.get(d.getContractId()));
            }
        }
        // 2. 附企业名 + 物流轨迹（买卖双方一次 getByTenantIds 批量取回，避免两次单点 Feign）
        Set<Long> tenantIds = new HashSet<>();
        tenantIds.add(d.getBuyerTenantId());
        tenantIds.add(d.getSupplierTenantId());
        tenantIds.remove(null);
        Map<Long, String> companyMap = tenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(tenantIds)).getData().values().stream()
                        .collect(Collectors.toMap(ProfileInfoDTO::getTenantId, ProfileInfoDTO::getCompanyName));
        vo.setBuyerCompanyName(companyMap.get(d.getBuyerTenantId()));
        vo.setSupplierCompanyName(companyMap.get(d.getSupplierTenantId()));

        Map<String, Object> result = new HashMap<>();
        result.put("delivery", vo);
        result.put("traces", deliveryMapper.selectTraceByDeliveryId(id));
        result.put("logisticsEvents", logisticsEventMapper.selectByDeliveryId(id));
        // 承诺记录（append-only 全量历史，双方可见）
        result.put("promiseDates", promiseDateMapper.selectByDeliveryId(id));
        return result;
    }

    /**
     * 供应方可建交付单的合同列表（已签署且尚有额度）
     * <p>
     * 原实现 JOIN 交易域 contract 表，改造后分两步：
     * ① 合同信息走 Feign（listSignedBySupplier，trade 侧按 create_time DESC 排好序）；
     * ② 分配量聚合走本域 delivery 表（selectAllocationStatsByContractIds）。
     * 在 Java 层应用原 HAVING 过滤：合同无数量 / 未分配满 / 有可让出额度的待发批，三者任一即可建。
     */
    @Override
    public List<CreatableContractRow> listCreatableContracts(Long tenantId) {
        // 1. 交易域拿该供应商所有已签署合同（Feign，顺序即 create_time DESC）
        List<ContractInfoDTO> signedContracts = contractClient.listSignedBySupplier(tenantId).getData();
        if (signedContracts == null || signedContracts.isEmpty()) {
            return List.of();
        }
        // 2. 收集合同ID，批量查本域分配量聚合（已分配量 / 待发货批次最大可让出量）
        List<Long> contractIds = signedContracts.stream()
                .map(ContractInfoDTO::getId)
                .filter(Objects::nonNull)
                .toList();
        // 2a. 批量查分配量聚合，转 contractId→Row Map 供逐合同取用
        Map<Long, ContractAllocationRow> allocationMap = contractIds.isEmpty() ? Map.of()
                : deliveryMapper.selectAllocationStatsByContractIds(contractIds).stream()
                        .collect(Collectors.toMap(ContractAllocationRow::getContractId, a -> a));

        // 3. 逐合同组装可建交付单结果（合同信息来自 Feign，聚合来自本域查询）
        List<CreatableContractRow> result = new ArrayList<>();
        for (ContractInfoDTO c : signedContracts) {
            // 3a. 本域聚合缺省为 0（合同下还没有交付单）
            ContractAllocationRow alloc = allocationMap.get(c.getId());
            long allocatedQty = alloc != null && alloc.getAllocatedQty() != null ? alloc.getAllocatedQty() : 0L;
            long maxPendingQty = alloc != null && alloc.getMaxPendingQty() != null ? alloc.getMaxPendingQty() : 0L;
            // 3b. 应用原 HAVING 过滤：无数量约束 / 未分配满 / 有待发批可让出（源批留 1，故 >1），三者任一即可建
            boolean creatable = c.getQuantity() == null
                    || allocatedQty < c.getQuantity()
                    || maxPendingQty > 1;
            if (!creatable) {
                continue;
            }
            // 3c. 组装返回行：合同字段取契约 DTO，聚合取本域查询
            CreatableContractRow row = new CreatableContractRow();
            row.setContractId(c.getId());
            row.setContractNo(c.getContractNo());
            row.setTitle(c.getTitle());
            row.setContractQty(c.getQuantity());
            row.setAmount(c.getAmount());
            row.setCurrency(c.getCurrency());
            row.setDeliveryDate(c.getDeliveryDate());
            row.setDeliveryPlace(c.getDeliveryPlace());
            row.setAllocatedQty(allocatedQty);
            row.setMaxPendingQty(maxPendingQty);
            result.add(row);
        }
        return result;
    }

    /**
     * 供应商发货排期列表（分批计划 + 发货状态）
     * <p>
     * 原实现 UNION JOIN 交易域 contract/contract_batch_plan 两表，改造后：
     * 合同信息走 Feign（listSignedBySupplier）、分批计划走 Feign（getBatchPlansByContractIds）、
     * 交付单走本域（selectByContractIds），在 Java 层完成「分批计划段 ∪ 补发批段」组装 + 过滤 + 排序 + 内存分页。
     */
    @Override
    public Map<String, Object> listShipmentPlan(Long tenantId, int pageNum, int pageSize, String keyword, String status) {
        int offset = (pageNum - 1) * pageSize;

        // 1. 交易域拿该供应商所有已签署合同（Feign，create_time DESC 顺序）
        List<ContractInfoDTO> contracts = contractClient.listSignedBySupplier(tenantId).getData();
        if (contracts == null || contracts.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("records", List.of());
            empty.put("total", 0L);
            empty.put("page", pageNum);
            empty.put("pageSize", pageSize);
            return empty;
        }
        // 2. 收集合同ID，Feign 批量拿分批计划（contractId → 计划列表）
        List<Long> contractIds = contracts.stream()
                .map(ContractInfoDTO::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<ContractBatchPlanDTO>> plansByContract = contractClient
                .getBatchPlansByContractIds(contractIds).getData();
        // 3. 本域批量查这些合同下的全部交付单（含补发批 reship_from 非空行）
        List<Delivery> deliveries = contractIds.isEmpty() ? List.of()
                : deliveryMapper.selectByContractIds(contractIds);

        // 4. Java 组装两段：分批计划段（计划左连交付单）+ 补发批段（无计划交付单），并应用 keyword/status 过滤
        List<ShipmentPlanRow> allRows = assembleShipmentPlan(contracts, plansByContract, deliveries, keyword, status);
        // 5. 按原 SQL 排序：contractId DESC, batchNo ASC
        allRows.sort(Comparator
                .comparing(ShipmentPlanRow::getContractId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShipmentPlanRow::getBatchNo, Comparator.nullsLast(Comparator.naturalOrder())));
        // 6. 内存分页（数据量=该供应商已签合同的分批计划 + 补发批，可控）
        long total = allRows.size();
        List<ShipmentPlanRow> records = offset >= allRows.size() ? List.of()
                : allRows.subList(offset, Math.min(offset + pageSize, allRows.size()));

        // 7. 回填采购方企业名（画像归供应商域，Feign 批量查询）
        Set<Long> buyerTenantIds = records.stream()
                .map(ShipmentPlanRow::getBuyerTenantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> companyMap = buyerTenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(buyerTenantIds)).getData().values().stream()
                        .collect(Collectors.toMap(ProfileInfoDTO::getTenantId, ProfileInfoDTO::getCompanyName));
        records.forEach(r -> r.setBuyerCompanyName(companyMap.get(r.getBuyerTenantId())));

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 是否存在进行中的交付单（终止合同前校验用） */
    @Override
    public boolean hasActiveDelivery(Long contractId) {
        return deliveryMapper.countActiveByContract(contractId) > 0;
    }

    // ==================== 私有工具 ====================

    /**
     * 组装发货排期行：分批计划段（计划左连交付单）+ 补发批段（无计划交付单），应用 keyword/status 过滤
     * <p>
     * 原 selectShipmentPlan SQL 为 UNION 两段（contract_batch_plan+contract+delivery 与 delivery+contract），
     * 改造后合同/分批计划来自 Feign，交付单来自本域，在内存完成两段合并与过滤。
     */
    private List<ShipmentPlanRow> assembleShipmentPlan(List<ContractInfoDTO> contracts,
                                                       Map<Long, List<ContractBatchPlanDTO>> plansByContract,
                                                       List<Delivery> deliveries,
                                                       String keyword, String status) {
        List<ShipmentPlanRow> rows = new ArrayList<>();
        // 1. 合同 id→DTO Map（补发批段按 contractId 取合同）
        Map<Long, ContractInfoDTO> contractMap = contracts.stream()
                .collect(Collectors.toMap(ContractInfoDTO::getId, c -> c));
        // 2. 交付单分流：普通批按 (contractId:batchNo) 索引供分批计划段匹配；补发批单独收集
        Map<String, Delivery> ordinaryDeliveryMap = new HashMap<>();
        List<Delivery> reshipDeliveries = new ArrayList<>();
        for (Delivery d : deliveries) {
            if (d.getReshipFrom() != null) {
                reshipDeliveries.add(d);
            } else {
                ordinaryDeliveryMap.put(d.getContractId() + ":" + d.getBatchNo(), d);
            }
        }
        // 3. 分批计划段：计划左连交付单（计划一定有，交付单可能无=未发货），keyword/status 过滤
        for (ContractInfoDTO c : contracts) {
            if (!matchKeyword(c, keyword)) {
                continue;
            }
            List<ContractBatchPlanDTO> plans = plansByContract.getOrDefault(c.getId(), List.of());
            for (ContractBatchPlanDTO plan : plans) {
                Delivery d = ordinaryDeliveryMap.get(plan.getContractId() + ":" + plan.getBatchNo());
                if (!matchPlanStatus(d, status)) {
                    continue;
                }
                rows.add(buildPlanRow(c, plan, d));
            }
        }
        // 4. 补发批段：reship_from 非空的交付单（无对应分批计划，batchPlanId 为空），keyword/status 过滤
        for (Delivery d : reshipDeliveries) {
            ContractInfoDTO c = contractMap.get(d.getContractId());
            if (c == null) {
                continue;
            }
            if (!matchKeyword(c, keyword)) {
                continue;
            }
            if (!matchReshipStatus(d, status)) {
                continue;
            }
            rows.add(buildReshipRow(c, d));
        }
        return rows;
    }

    /** 关键词过滤：合同编号或标题包含关键词（原 SQL c.contract_no/title LIKE） */
    private boolean matchKeyword(ContractInfoDTO c, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return (c.getContractNo() != null && c.getContractNo().contains(keyword))
                || (c.getTitle() != null && c.getTitle().contains(keyword));
    }

    /** 分批计划段状态过滤（原 SQL 第一段 choose）：pending=无交付单；pending,returned=无或已退回；否则精确匹配 */
    private boolean matchPlanStatus(Delivery d, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        if ("pending".equals(status)) {
            return d == null;
        }
        if ("pending,returned".equals(status)) {
            return d == null || "returned".equals(d.getStatus());
        }
        return d != null && status.equals(d.getStatus());
    }

    /** 补发批段状态过滤（原 SQL 第二段 choose）：pending 段恒排除（补发批无"未发货"概念）；其余同语义 */
    private boolean matchReshipStatus(Delivery d, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        if ("pending".equals(status)) {
            return false;
        }
        if ("pending,returned".equals(status)) {
            return "reship_pending".equals(d.getStatus()) || "returned".equals(d.getStatus());
        }
        return status.equals(d.getStatus());
    }

    /** 分批计划段行组装：计划字段取分批计划 DTO，合同字段取契约 DTO，交付单字段可能为空（未发货） */
    private ShipmentPlanRow buildPlanRow(ContractInfoDTO c, ContractBatchPlanDTO plan, Delivery d) {
        ShipmentPlanRow row = new ShipmentPlanRow();
        row.setBatchPlanId(plan.getId());
        row.setContractId(plan.getContractId());
        row.setBatchNo(plan.getBatchNo());
        row.setPlanQty(plan.getQuantity());
        row.setPlanDeliveryDate(plan.getDeliveryDate());
        row.setPlanRemark(plan.getRemark());
        fillContractFields(row, c);
        fillDeliveryFields(row, d);
        return row;
    }

    /** 补发批段行组装：无分批计划，batchPlanId 为空，计划数量/交期取交付单自身值（原 SQL d.quantity/d.delivery_date） */
    private ShipmentPlanRow buildReshipRow(ContractInfoDTO c, Delivery d) {
        ShipmentPlanRow row = new ShipmentPlanRow();
        row.setBatchPlanId(null);
        row.setContractId(d.getContractId());
        row.setBatchNo(d.getBatchNo());
        row.setPlanQty(d.getQuantity());
        row.setPlanDeliveryDate(d.getDeliveryDate());
        row.setPlanRemark(null);
        fillContractFields(row, c);
        fillDeliveryFields(row, d);
        return row;
    }

    /** 合同侧字段回填（两段共用） */
    private void fillContractFields(ShipmentPlanRow row, ContractInfoDTO c) {
        row.setContractNo(c.getContractNo());
        row.setContractTitle(c.getTitle());
        row.setBuyerTenantId(c.getBuyerTenantId());
        row.setDeliveryPlace(c.getDeliveryPlace());
        row.setDeliveryTerms(c.getDeliveryTerms());
        row.setCurrency(c.getCurrency());
        row.setContractAmount(c.getAmount());
    }

    /** 交付单侧字段回填（两段共用；d 可能为 null=未发货） */
    private void fillDeliveryFields(ShipmentPlanRow row, Delivery d) {
        if (d == null) {
            return;
        }
        row.setDeliveryId(d.getId());
        row.setDeliveryNo(d.getDeliveryNo());
        row.setDeliveryStatus(d.getStatus());
        row.setDeliveryQty(d.getQuantity());
        row.setShipCompany(d.getShipCompany());
        row.setShipTrackingNo(d.getShipTrackingNo());
        row.setShipQty(d.getShipQty());
        row.setShipDate(d.getShipDate());
        row.setShipAttachmentKey(d.getShipAttachmentKey());
        row.setReceiveQty(d.getReceiveQty());
        row.setReturnReason(d.getReturnReason());
        row.setDeliveryCreateTime(d.getCreateTime());
    }

    /** 状态筛选：支持逗号分隔多状态，空则不过滤 */
    private List<String> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        List<String> list = Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return list.isEmpty() ? null : list;
    }

    /** 查交付单并校验归属（采购方或供应方）——查询侧私有副本（读），写侧基座校验在 DeliveryLifecycleService */
    private Delivery getDelivery(Long id, Long tenantId) {
        Delivery d = deliveryMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.DELIVERY_NOT_FOUND);
        }
        if (!tenantId.equals(d.getBuyerTenantId()) && !tenantId.equals(d.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return d;
    }
}
