package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.InquiryInvite;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryInviteMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.projection.ActiveConnectionInquiryRow;
import cn.greensmartplanet.gsc.trade.projection.InviteStatusCountRow;
import cn.greensmartplanet.gsc.trade.projection.QuoteCountRow;
import cn.greensmartplanet.gsc.trade.service.InquiryQueryService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.vo.InquiryInviteVO;
import cn.greensmartplanet.gsc.trade.vo.InquiryVO;
import cn.greensmartplanet.gsc.trade.vo.QuoteVO;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 询盘查询服务实现 — 列表 / 详情 / 供应商ID集合 / 对接活跃状态 / 内部契约
 * <p>
 * 只读查询，无 @Transactional。批量填充统计/公司名采用先收集 ID → 一次查询 → Map 组装（红线 1，消 N+1）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryQueryServiceImpl implements InquiryQueryService {

    private final InquiryMapper inquiryMapper;
    private final InquiryInviteMapper inviteMapper;
    private final QuoteMapper quoteMapper;
    private final InquiryAwardMapper inquiryAwardMapper;
    private final ContractMapper contractMapper;
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;  // 组装 VO 时按 oss_key 现签附件 URL

    /**
     * 分页查询盘（采购/供应双视角），批量填充受邀/报价/授标统计
     */
    @Override
    public Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                                    String status, String keyword, String role) {
        // 1. 按角色分视角分页查询（供应商只看受邀的 JOIN inquiry_invite，采购方看自己发起的）
        int offset = (pageNum - 1) * pageSize;
        List<Inquiry> records;
        long total;
        if ("supplier".equals(role)) {
            records = inquiryMapper.selectBySupplier(tenantId, status, keyword, offset, pageSize);
            total = inquiryMapper.countBySupplier(tenantId, status, keyword);
        } else {
            records = inquiryMapper.selectByBuyer(tenantId, status, keyword, offset, pageSize);
            total = inquiryMapper.countByBuyer(tenantId, status, keyword);
        }

        // 2. 当前页询盘可能来自多个采购方：收集采购方租户 ID，一次性批量查公司名（消 N+1）
        Set<Long> buyerTenantIds = records.stream()
                .map(Inquiry::getBuyerTenantId)
                .collect(Collectors.toSet());
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(buyerTenantIds);

        List<InquiryVO> vos = records.stream().map(i -> {
            InquiryVO vo = InquiryVO.fromEntity(i, ossService);
            vo.setBuyerCompanyName(companyNames.get(i.getBuyerTenantId()));
            return vo;
        }).collect(Collectors.toList());

        // 3. 采购方视角：批量填充受邀状态统计 + 报价数 + 授标进度
        if (!"supplier".equals(role) && !vos.isEmpty()) {
            List<Long> inquiryIds = vos.stream().map(InquiryVO::getId).collect(Collectors.toList());

            // 3a. 批量查按状态分组的邀请数（pending/accepted/declined）
            List<InviteStatusCountRow> statusCounts = inviteMapper.countByInquiryIdsGroupByStatus(inquiryIds);
            Map<Long, Map<String, Integer>> inviteStats = new HashMap<>();
            for (InviteStatusCountRow row : statusCounts) {
                if (row.getInquiryId() != null) {
                    inviteStats.computeIfAbsent(row.getInquiryId(), k -> new HashMap<>())
                            .put(row.getInviteStatus(), row.getSupplierCount() != null ? row.getSupplierCount().intValue() : 0);
                }
            }
            for (InquiryVO vo : vos) {
                Map<String, Integer> stats = inviteStats.get(vo.getId());
                if (stats != null) {
                    int sum = stats.values().stream().mapToInt(Integer::intValue).sum();
                    vo.setSupplierCount(sum);
                    vo.setAcceptedSupplierCount(stats.getOrDefault("accepted", 0));
                    vo.setDeclinedSupplierCount(stats.getOrDefault("declined", 0));
                } else {
                    vo.setSupplierCount(0);
                    vo.setAcceptedSupplierCount(0);
                    vo.setDeclinedSupplierCount(0);
                }
            }

            // 3b. 批量填充报价数
            List<QuoteCountRow> quoteCounts = quoteMapper.countByInquiryIds(inquiryIds);
            Map<Long, Long> quoteCountMap = new HashMap<>();
            for (QuoteCountRow row : quoteCounts) {
                if (row.getInquiryId() != null) quoteCountMap.put(row.getInquiryId(), row.getQuoteCount() != null ? row.getQuoteCount() : 0L);
            }
            for (InquiryVO vo : vos) {
                Long cnt = quoteCountMap.get(vo.getId());
                vo.setQuoteCount(cnt != null ? cnt.intValue() : 0);
            }

            // 3c. 批量填充已授数量/家数
            fillAwardProgress(vos);
        }

        // 4. 供应商视角：批量填充 myInviteStatus / myInviteId / 报价 / 授标信息
        if ("supplier".equals(role) && !vos.isEmpty()) {
            List<Long> inquiryIds = vos.stream().map(InquiryVO::getId).collect(Collectors.toList());

            // 4a. 批量查我的邀请（inquiryId → invite）
            List<InquiryInvite> myInvites = inviteMapper.selectByInquiryIdsAndSupplier(inquiryIds, tenantId);
            Map<Long, InquiryInvite> inviteMap = new HashMap<>();
            for (InquiryInvite inv : myInvites) {
                inviteMap.put(inv.getInquiryId(), inv);
            }

            // 4b. 批量查我的报价（inquiryId → quote）
            List<Quote> myQuotes = quoteMapper.selectByInquiryIdsAndSupplier(inquiryIds, tenantId);
            Map<Long, Quote> quoteMap = new HashMap<>();
            for (Quote q : myQuotes) {
                quoteMap.put(q.getInquiryId(), q);
            }

            // 4c. 批量查我的授标明细（quoteId → 授标数量）
            List<InquiryAward> myAwards = inquiryAwardMapper.selectByInquiryIdsAndSupplier(inquiryIds, tenantId);
            Map<Long, Integer> awardQtyMap = new HashMap<>();
            for (InquiryAward a : myAwards) {
                if (a.getAwardedQuantity() != null) {
                    awardQtyMap.put(a.getQuoteId(), a.getAwardedQuantity());
                }
            }

            // 4d. 批量查报价总数
            List<QuoteCountRow> quoteCounts = quoteMapper.countByInquiryIds(inquiryIds);
            Map<Long, Long> quoteCountMap = new HashMap<>();
            for (QuoteCountRow row : quoteCounts) {
                if (row.getInquiryId() != null) quoteCountMap.put(row.getInquiryId(), row.getQuoteCount() != null ? row.getQuoteCount() : 0L);
            }

            // 4e. 组装供应商视角字段
            for (InquiryVO vo : vos) {
                Long qcnt = quoteCountMap.get(vo.getId());
                vo.setQuoteCount(qcnt != null ? qcnt.intValue() : 0);
                InquiryInvite inv = inviteMap.get(vo.getId());
                if (inv != null) {
                    vo.setMyInviteStatus(inv.getInviteStatus());
                    vo.setMyInviteId(inv.getId());
                }
                Quote q = quoteMap.get(vo.getId());
                if (q != null) {
                    vo.setMyQuoteId(q.getId());
                    vo.setMyQuotePrice(q.getTotalPrice());
                    vo.setMyQuoteUnitPrice(q.getUnitPrice());
                    vo.setMyQuoteCurrency(q.getCurrency());
                    vo.setMyQuoteIsWinning(q.getIsWinning() != null && q.getIsWinning() == 1);
                    vo.setMyQuoteStatus(q.getStatus());
                    // 授标信息：从自己的报价取授标数量，中标金额 = 单价 × 授标数量
                    Integer awardedQty = awardQtyMap.get(q.getId());
                    if (awardedQty != null) {
                        vo.setMyAwardedQuantity(awardedQty);
                        vo.setMyAwardedCurrency(q.getCurrency());
                        if (q.getUnitPrice() != null) {
                            vo.setMyAwardedAmount(q.getUnitPrice().multiply(BigDecimal.valueOf(awardedQty)));
                        }
                    }
                }
            }
        }

        // 5. 组装分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", vos);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 询盘详情：权限校验（采购方或被邀供应商），附受邀名单/报价列表/授标与合同状态
     */
    @Override
    public InquiryVO getDetail(Long id, Long tenantId, String role) {
        // 1. 询盘必须存在
        Inquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException(ErrorCode.PROCUREMENT_INQUIRY_NOT_FOUND);
        }

        // 2. 权限校验（红线 2）：只有采购方或被邀请的供应商能看
        if (!tenantId.equals(inquiry.getBuyerTenantId())) {
            List<InquiryInvite> allInvites = inviteMapper.selectByInquiryId(id);
            boolean isInvited = allInvites.stream().anyMatch(inv -> tenantId.equals(inv.getSupplierTenantId()));
            if (!isInvited) {
                throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
            }
        }

        // 3. 受邀名单（供应商视角只返回自己的邀请记录）
        List<InquiryInvite> invites = inviteMapper.selectByInquiryId(id);
        if ("supplier".equals(role)) {
            invites = invites.stream()
                    .filter(inv -> tenantId.equals(inv.getSupplierTenantId()))
                    .collect(Collectors.toList());
        }

        // 4. 报价列表（报价保密：采购方看全部，供应商只看自己——Mapper 层 WHERE 控制）
        List<Quote> quotes = quoteMapper.selectByInquiryId(id, tenantId);

        // 5. 收集本页所有需要公司名的租户ID（买家 + 受邀供应商 + 报价供应商），一次批量查询消 N+1
        Set<Long> companyNameTenantIds = new HashSet<>();
        companyNameTenantIds.add(inquiry.getBuyerTenantId());
        invites.forEach(inv -> companyNameTenantIds.add(inv.getSupplierTenantId()));
        quotes.forEach(q -> companyNameTenantIds.add(q.getSupplierTenantId()));
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(companyNameTenantIds);

        InquiryVO vo = InquiryVO.fromEntity(inquiry, ossService);
        vo.setBuyerCompanyName(companyNames.get(inquiry.getBuyerTenantId()));

        // 5b. 是否已创建合同（采购方视角）
        if (tenantId.equals(inquiry.getBuyerTenantId())) {
            vo.setContractExists(contractMapper.countByInquiryId(id) > 0);
        }

        // 5c. 受邀名单 VO（公司名直接从批量结果取）
        List<InquiryInviteVO> inviteVOs = invites.stream().map(inv -> {
            InquiryInviteVO iv = InquiryInviteVO.fromEntity(inv);
            iv.setSupplierCompanyName(companyNames.get(inv.getSupplierTenantId()));
            return iv;
        }).collect(Collectors.toList());
        vo.setInvites(inviteVOs);
        vo.setSupplierCount(invites.size());

        // 5d. 供应商视角回填我的邀请状态
        if ("supplier".equals(role) && !invites.isEmpty()) {
            vo.setMyInviteStatus(invites.get(0).getInviteStatus());
            vo.setMyInviteId(invites.get(0).getId());
        }

        // 6. 授标数量 map（quoteId → awardedQuantity）
        Map<Long, Integer> awardQtyMap = new HashMap<>();
        List<InquiryAward> awards = inquiryAwardMapper.selectByInquiryId(id);
        if (awards != null) {
            for (InquiryAward a : awards) {
                if (a.getAwardedQuantity() != null) {
                    awardQtyMap.put(a.getQuoteId(), a.getAwardedQuantity());
                }
            }
        }

        // 7. 合同 map（winning_quote_id → contract），比价详情按行判断"是否已创建合同"
        Map<Long, Contract> contractMap = new HashMap<>();
        List<Long> quoteIds = quotes.stream().map(Quote::getId).collect(Collectors.toList());
        if (!quoteIds.isEmpty()) {
            List<Contract> contracts = contractMapper.selectByWinningQuoteIds(quoteIds);
            if (contracts != null) {
                for (Contract c : contracts) {
                    if (c.getWinningQuoteId() != null && !contractMap.containsKey(c.getWinningQuoteId())) {
                        contractMap.put(c.getWinningQuoteId(), c);
                    }
                }
            }
        }

        // 8. 组装报价 VO（授标数量/合同状态 + 公司名从批量结果取）
        List<QuoteVO> quoteVOs = quotes.stream().map(q -> {
            QuoteVO qv = QuoteVO.fromEntity(q, ossService);
            qv.setSupplierCompanyName(companyNames.get(q.getSupplierTenantId()));
            Integer awardedQty = awardQtyMap.get(q.getId());
            qv.setAwardedQuantity(awardedQty);
            if (awardedQty != null && q.getUnitPrice() != null) {
                qv.setAwardedAmount(q.getUnitPrice().multiply(BigDecimal.valueOf(awardedQty)));
            }
            Contract c = contractMap.get(q.getId());
            if (c != null) {
                qv.setContractId(c.getId());
                qv.setContractStatus(c.getStatus());
                qv.setSupplierConfirmed(c.getSupplierConfirmed());
            }
            return qv;
        }).collect(Collectors.toList());
        vo.setQuotes(quoteVOs);
        vo.setQuoteCount(quotes.size());

        // 9. 回填已授数量/家数
        fillAwardProgress(vo, awards);
        return vo;
    }

    /**
     * 查某需求下已邀请（含已授标）的供应商ID集合（前端"已授标"集合，禁选用）
     */
    @Override
    public List<Long> getInvitedSupplierIds(Long tenantId, Long requirementId) {
        if (requirementId == null) return List.of();
        return inviteMapper.selectInvitedSupplierIdsByBuyer(tenantId, requirementId);
    }

    /**
     * 查某需求下进行中询盘已邀请的供应商ID集合（前端"询价中"集合，禁选用）
     */
    @Override
    public List<Long> getActiveInquirySupplierIds(Long tenantId, Long requirementId) {
        if (requirementId == null) return List.of();
        return inviteMapper.selectActiveInquirySupplierIdsByRequirement(tenantId, requirementId);
    }

    /**
     * 批量查对接记录对应的进行中询盘状态（connectionId→status）
     */
    @Override
    public Map<Long, String> getActiveInquiryMap(Long tenantId, List<Long> connectionIds) {
        if (connectionIds == null || connectionIds.isEmpty()) return Collections.emptyMap();
        List<ActiveConnectionInquiryRow> rows = inquiryMapper.selectActiveByConnectionIds(connectionIds, tenantId);
        Map<Long, String> result = new HashMap<>();
        for (ActiveConnectionInquiryRow row : rows) {
            if (row.getConnectionId() != null && row.getInquiryStatus() != null) {
                result.put(row.getConnectionId(), row.getInquiryStatus());
            }
        }
        return result;
    }

    /**
     * 按需求ID查拒绝过相关询盘的供应商租户ID集合（内部契约，供应商域智能匹配页标注）
     */
    @Override
    public List<Long> getDeclinedSupplierIds(Long requirementId, Long buyerTenantId) {
        return inviteMapper.selectDeclinedSupplierIdsByRequirement(requirementId, buyerTenantId);
    }

    /**
     * 按当前租户分页查询盘摘要（AI 工具 list_inquiries 专用）
     * <p>
     * 复用 list 的 buyer/supplier 双视角查询，但只返回 AI 友好的摘要字段子集，
     * 不填充受邀统计/报价/授标等与「定位询盘 + 发起写操作」无关的明细。
     */
    @Override
    public List<AiInquirySummaryDTO> listForAi(AiInquiryQuery query) {
        // 1. 兜底分页参数（模型可能不传 page/pageSize）
        int pageNum = query.getPage() != null ? query.getPage() : 1;
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;
        String role = query.getRole() == null ? "buyer" : query.getRole();
        int offset = (pageNum - 1) * pageSize;

        // 2. 复用双视角查询（供应商只看受邀的，采购方看自己发起的）
        List<Inquiry> records = "supplier".equals(role)
                ? inquiryMapper.selectBySupplier(query.getTenantId(), query.getStatus(), query.getKeyword(), offset, pageSize)
                : inquiryMapper.selectByBuyer(query.getTenantId(), query.getStatus(), query.getKeyword(), offset, pageSize);

        // 3. 实体 → 摘要 DTO（只保留 AI 定位与展示所需的字段）
        return records.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    /**
     * 按询盘ID查单条摘要（AI 域拼确认文案用）
     * <p>
     * 归属校验（红线 2）：仅采购方自己发起/受邀的询盘返回摘要，其余返回 null（AI 域据此拒绝提案）。
     */
    @Override
    public AiInquirySummaryDTO getForAi(AiInquiryQuery query) {
        // 1. 询盘必须存在
        Inquiry inquiry = inquiryMapper.selectById(query.getInquiryId());
        if (inquiry == null) {
            return null;
        }
        // 2. 归属校验：采购方看自己发起的，供应商看受邀的（与 getDetail 一致）
        if (query.getTenantId().equals(inquiry.getBuyerTenantId())) {
            return toSummary(inquiry);
        }
        boolean invited = inviteMapper.selectByInquiryId(query.getInquiryId()).stream()
                .anyMatch(inv -> query.getTenantId().equals(inv.getSupplierTenantId()));
        return invited ? toSummary(inquiry) : null;
    }

    /**
     * 实体 → 摘要 DTO（listForAi / getForAi 共用）
     */
    private AiInquirySummaryDTO toSummary(Inquiry i) {
        return AiInquirySummaryDTO.builder()
                .id(i.getId())
                .inquiryNo(i.getInquiryNo())
                .title(i.getTitle())
                .category(i.getCategory())
                .quantity(i.getQuantity())
                .unit(i.getUnit())
                .deliveryDate(i.getDeliveryDate())
                .quoteDeadline(i.getQuoteDeadline())
                .paymentTerms(i.getPaymentTerms())
                .status(i.getStatus())
                .statusText(InquiryStatus.descOf(i.getStatus()))
                .build();
    }

    /**
     * 列表批量填充已授数量/家数
     */
    private void fillAwardProgress(List<InquiryVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        // 1. 批量查授标明细，按询盘ID分组
        List<Long> ids = vos.stream().map(InquiryVO::getId).collect(Collectors.toList());
        List<InquiryAward> awards = inquiryAwardMapper.selectByInquiryIds(ids);
        Map<Long, List<InquiryAward>> grouped = new HashMap<>();
        if (awards != null) {
            for (InquiryAward a : awards) {
                grouped.computeIfAbsent(a.getInquiryId(), k -> new ArrayList<>()).add(a);
            }
        }
        // 2. 逐条回填
        for (InquiryVO vo : vos) {
            fillAwardProgress(vo, grouped.get(vo.getId()));
        }
    }

    /**
     * 单条询盘回填已授数量/中标家数
     */
    private void fillAwardProgress(InquiryVO vo, List<InquiryAward> awards) {
        int qty = 0;
        int cnt = 0;
        if (awards != null) {
            cnt = awards.size();
            for (InquiryAward a : awards) {
                if (a.getAwardedQuantity() != null) {
                    qty += a.getAwardedQuantity();
                }
            }
        }
        vo.setAwardedQuantityTotal(qty);
        vo.setAwardedSupplierCount(cnt);
    }
}
