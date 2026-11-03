package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.AiContractQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiContractSummaryDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractBatchPlanDTO;
import cn.greensmartplanet.gsc.api.trade.dto.ContractInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.trade.dto.BatchPlanItem;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.ContractBatchPlanService;
import cn.greensmartplanet.gsc.trade.service.ContractQueryService;
import cn.greensmartplanet.gsc.trade.support.ContractAccessValidator;
import cn.greensmartplanet.gsc.trade.support.ContractAmountHelper;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.vo.ContractVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合同查询服务实现 — 列表 / 详情 / 活跃合同报价 / 内部契约查询
 * <p>
 * 只读查询无 @Transactional；列表/详情公司名、分批计划均先收集 ID 批量查询再组装（红线 1，消 N+1）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractQueryServiceImpl implements ContractQueryService {

    private final ContractMapper contractMapper;
    private final InquiryMapper inquiryMapper;
    private final QuoteMapper quoteMapper;
    private final ContractBatchPlanService contractBatchPlanService;
    private final ContractAccessValidator contractAccessValidator;
    private final ContractAmountHelper contractAmountHelper;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /**
     * 分页查合同（采购/供应双视角），批量回填询盘编号、企业名、分批计划
     */
    @Override
    public Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                                    String status, String keyword, String role) {
        // 1. 按角色分采购/供应视角分页查询
        int offset = (pageNum - 1) * pageSize;
        List<Contract> records;
        long total;
        if ("supplier".equals(role)) {
            records = contractMapper.selectBySupplier(tenantId, status, keyword, offset, pageSize);
            total = contractMapper.countBySupplier(tenantId, status, keyword);
        } else {
            records = contractMapper.selectByBuyer(tenantId, status, keyword, offset, pageSize);
            total = contractMapper.countByBuyer(tenantId, status, keyword);
        }

        // 2. 批量回填询盘编号（支持同一询盘下多份合同区分，消除按条二次查询）
        Set<Long> inquiryIds = records.stream().map(Contract::getInquiryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> inquiryNoMap = new HashMap<>();
        if (!inquiryIds.isEmpty()) {
            inquiryMapper.selectBatchIds(inquiryIds)
                    .forEach(iq -> inquiryNoMap.put(iq.getId(), iq.getInquiryNo()));
        }

        // 3. 批量回填分批计划（编辑预填用，消除按条二次查询）
        Set<Long> batchContractIds = records.stream().map(Contract::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<BatchPlanItem>> batchPlanMap = contractBatchPlanService.listByContractIds(new ArrayList<>(batchContractIds));

        // 4. 收集本页全部买卖方租户ID，一次批量查企业名（消 N+1）
        Set<Long> tenantIds = new HashSet<>();
        records.forEach(c -> {
            tenantIds.add(c.getBuyerTenantId());
            tenantIds.add(c.getSupplierTenantId());
        });
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(tenantIds);

        // 5. 组装 VO
        List<ContractVO> vos = records.stream().map(c -> {
            ContractVO vo = ContractVO.fromEntity(c, ossService);
            vo.setBuyerCompanyName(companyNames.get(c.getBuyerTenantId()));
            vo.setSupplierCompanyName(companyNames.get(c.getSupplierTenantId()));
            vo.setInquiryNo(inquiryNoMap.get(c.getInquiryId()));
            vo.setBatchPlans(batchPlanMap.get(c.getId()));
            return vo;
        }).collect(Collectors.toList());

        // 6. 组装分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", vos);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 合同详情：任一方归属校验（红线 2），附询盘标题/编号、中标报价金额、分批计划
     */
    @Override
    public ContractVO getDetail(Long id, Long tenantId) {
        // 1. 归属校验：仅采购方/供应方可查看（修越权，红线 2）
        Contract contract = contractAccessValidator.getContractAnyParty(id, tenantId);
        ContractVO vo = ContractVO.fromEntity(contract, ossService);
        // 2. 批量查双方企业名（一次 Feign 批量）
        Set<Long> tenantIds = new HashSet<>();
        tenantIds.add(contract.getBuyerTenantId());
        tenantIds.add(contract.getSupplierTenantId());
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(tenantIds);
        vo.setBuyerCompanyName(companyNames.get(contract.getBuyerTenantId()));
        vo.setSupplierCompanyName(companyNames.get(contract.getSupplierTenantId()));
        // 3. 分批计划（编辑预填用）
        vo.setBatchPlans(contractBatchPlanService.listByContractId(contract.getId()));
        // 4. 关联询盘标题 / 中标报价金额：详情"关联信息"展示业务数据而非编号
        if (contract.getInquiryId() != null) {
            Inquiry inquiry = inquiryMapper.selectById(contract.getInquiryId());
            if (inquiry != null) {
                vo.setInquiryTitle(inquiry.getTitle());
                vo.setInquiryNo(inquiry.getInquiryNo());
            }
        }
        if (contract.getWinningQuoteId() != null) {
            Quote quote = quoteMapper.selectById(contract.getWinningQuoteId());
            if (quote != null) {
                vo.setQuoteAmount(contractAmountHelper.deriveContractAmount(quote));
            }
        }
        return vo;
    }

    /**
     * 查某询盘下已有活跃（非终止）合同的 winning_quote_id 列表（前端禁选用）
     */
    @Override
    public List<Long> getActiveContractQuoteIds(Long inquiryId) {
        return contractMapper.selectActiveWinningQuoteIdsByInquiryId(inquiryId);
    }

    /**
     * 按合同ID查合同信息契约 DTO（内部契约，不存在返回 null）
     */
    @Override
    public ContractInfoDTO getContractInfoById(Long id) {
        Contract contract = contractMapper.selectById(id);
        return contract == null ? null : toContractInfoDTO(contract);
    }

    /**
     * 批量按合同ID查合同信息契约 DTO（内部契约，不存在的合同不在 map 中）
     */
    @Override
    public Map<Long, ContractInfoDTO> getContractInfoByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ContractInfoDTO> map = new HashMap<>();
        for (Contract contract : contractMapper.selectBatchIds(ids)) {
            map.put(contract.getId(), toContractInfoDTO(contract));
        }
        return map;
    }

    /**
     * 批量按合同ID查询盘编号（内部契约，无询盘或合同不存在的不在 map 中）
     */
    @Override
    public Map<Long, String> getInquiryNosByContractIds(List<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Contract contract : contractMapper.selectBatchIds(contractIds)) {
            if (contract.getInquiryId() != null) {
                Inquiry inquiry = inquiryMapper.selectById(contract.getInquiryId());
                if (inquiry != null) {
                    map.put(contract.getId(), inquiry.getInquiryNo());
                }
            }
        }
        return map;
    }

    /**
     * 按供应商租户查已签署合同契约 DTO（内部契约，交付域列可建交付单合同/发货排期用）
     */
    @Override
    public List<ContractInfoDTO> listSignedBySupplier(Long supplierTenantId) {
        if (supplierTenantId == null) {
            return List.of();
        }
        // 1. 本域按供应商租户查已签署合同实体（XML 内 create_time DESC 排序，交付侧顺序即最终展示顺序）
        List<Contract> contracts = contractMapper.selectSignedBySupplier(supplierTenantId);
        // 2. 实体批量转契约 DTO（只带交付域需要的字段子集，避免整实体跨服务传输）
        return contracts.stream()
                .map(this::toContractInfoDTO)
                .collect(Collectors.toList());
    }

    /**
     * 批量按合同ID查分批计划契约 DTO（内部契约，交付域发货排期/完成聚合用）
     */
    @Override
    public Map<Long, List<ContractBatchPlanDTO>> getBatchPlansByContractIds(List<Long> contractIds) {
        return contractBatchPlanService.listContractBatchPlanDTOsByContractIds(contractIds);
    }

    /**
     * 合同实体 → 契约 DTO（仅交付域需要的字段子集）
     */
    private ContractInfoDTO toContractInfoDTO(Contract c) {
        return ContractInfoDTO.builder()
                .id(c.getId())
                .contractNo(c.getContractNo())
                .title(c.getTitle())
                .inquiryId(c.getInquiryId())
                .buyerTenantId(c.getBuyerTenantId())
                .supplierTenantId(c.getSupplierTenantId())
                .amount(c.getAmount())
                .quantity(c.getQuantity())
                .currency(c.getCurrency())
                .deliveryTerms(c.getDeliveryTerms())
                .deliveryPlace(c.getDeliveryPlace())
                .deliveryDate(c.getDeliveryDate())
                .status(c.getStatus())
                .createBy(c.getCreateBy())
                .productionNeedDate(c.getProductionNeedDate())
                .materialCriticalLevel(c.getMaterialCriticalLevel())
                .inventoryCoverDays(c.getInventoryCoverDays())
                .plannedShipDate(c.getPlannedShipDate())
                .allowedDelayDays(c.getAllowedDelayDays())
                .partialDeliveryAllowed(c.getPartialDeliveryAllowed())
                .exceptionNoticeHours(c.getExceptionNoticeHours())
                .penaltyDailyRate(c.getPenaltyDailyRate())
                .penaltyMaxRate(c.getPenaltyMaxRate())
                .build();
    }

    /**
     * AI 工具 list_contracts：按当前租户分页查合同摘要（buyer/supplier 双视角）
     */
    @Override
    public List<AiContractSummaryDTO> listForAi(AiContractQuery query) {
        // tenantId 由调用方从登录态注入，本处只做非空防御（不应发生，发生即拒）
        Long tenantId = query == null ? null : query.getTenantId();
        if (tenantId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少租户上下文");
        }
        // 分页参数兜底 + 防越界（红线 5）
        int page = query.getPage() == null ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePage - 1) * safeSize;
        // 1. 双视角分页查询（复用 list 的角色路由逻辑；默认 buyer 视角）
        List<Contract> records = "supplier".equals(query.getRole())
                ? contractMapper.selectBySupplier(tenantId, query.getStatus(), query.getKeyword(), offset, safeSize)
                : contractMapper.selectByBuyer(tenantId, query.getStatus(), query.getKeyword(), offset, safeSize);
        // 2. 实体 → AI 摘要 DTO（只带模型作答需要的字段子集）
        return records.stream()
                .map(c -> AiContractSummaryDTO.builder()
                        .id(c.getId())
                        .contractNo(c.getContractNo())
                        .title(c.getTitle())
                        .amount(c.getAmount())
                        .currency(c.getCurrency())
                        .status(c.getStatus())
                        .statusText(ContractStatus.descOf(c.getStatus()))
                        .buyerTenantId(c.getBuyerTenantId())
                        .supplierTenantId(c.getSupplierTenantId())
                        .deliveryDate(c.getDeliveryDate())
                        .signDate(c.getSignDate())
                        .build())
                .collect(Collectors.toList());
    }
}
