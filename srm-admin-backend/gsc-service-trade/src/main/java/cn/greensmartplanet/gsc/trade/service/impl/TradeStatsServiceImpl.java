package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.trade.dto.TradeStatsDTO;
import cn.greensmartplanet.gsc.common.enums.ContractStatus;
import cn.greensmartplanet.gsc.trade.enums.InquiryStatus;
import cn.greensmartplanet.gsc.trade.mapper.TradeStatsMapper;
import cn.greensmartplanet.gsc.trade.service.TradeStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作台统计服务实现 — 询盘/合同计数（SQL 从旧单体 DashboardMapper 按表归属迁入）
 * <p>
 * 状态集合与原单体 DashboardServiceImpl 常量逐项一致：
 * INQUIRY_ACTIVE=(open,quoting)、INQUIRY_QUOTING=(quoting)、
 * CONTRACT_ACTIVE=(pending_supplier,pending_confirm,signed)、
 * CONTRACT_PENDING_SUPPLIER=(pending_supplier)、CONTRACT_PENDING_CONFIRM=(pending_confirm)。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeStatsServiceImpl implements TradeStatsService {

    private static final List<String> INQUIRY_ACTIVE = InquiryStatus.ACTIVE_CODES;
    private static final List<String> INQUIRY_QUOTING = List.of(InquiryStatus.QUOTING.getCode());
    private static final List<String> CONTRACT_ACTIVE = List.of(ContractStatus.PENDING_SUPPLIER.getCode(), ContractStatus.PENDING_CONFIRM.getCode(), ContractStatus.SIGNED.getCode());
    private static final List<String> CONTRACT_PENDING_SUPPLIER = List.of(ContractStatus.PENDING_SUPPLIER.getCode());
    private static final List<String> CONTRACT_PENDING_CONFIRM = List.of(ContractStatus.PENDING_CONFIRM.getCode());

    private final TradeStatsMapper statsMapper;

    /** 工作台询盘/合同计数（tenantId=null 为超管，只取平台级合同总数） */
    @Override
    public TradeStatsDTO getDashboardStats(Long tenantId) {
        // 1. 超管：只取平台级合同总数，其余字段保持 0（工作台超管面板不展示租户视角指标）
        if (tenantId == null) {
            return TradeStatsDTO.builder()
                    .contractAll(statsMapper.countAllContract())
                    .build();
        }

        // 2. 采购/供应视角：一次查齐 10 项计数，BFF 按角色取用对应字段
        return TradeStatsDTO.builder()
                // 2a. 采购方：进行中/待比价询盘 + 进行中/待确认/待盖章合同
                .inquiryActive(statsMapper.countInquiryByBuyer(tenantId, INQUIRY_ACTIVE))
                .inquiryQuoting(statsMapper.countInquiryByBuyer(tenantId, INQUIRY_QUOTING))
                .contractActiveBuyer(statsMapper.countContractByBuyer(tenantId, CONTRACT_ACTIVE))
                .contractPendingSupplierBuyer(statsMapper.countContractByBuyer(tenantId, CONTRACT_PENDING_SUPPLIER))
                .contractPendingConfirmBuyer(statsMapper.countContractByBuyer(tenantId, CONTRACT_PENDING_CONFIRM))
                // 2b. 供应方：进行中/待确认/待盖章合同 + 待报价 + 已中标
                .contractActiveSupplier(statsMapper.countContractBySupplier(tenantId, CONTRACT_ACTIVE))
                .contractPendingSupplierSupplier(statsMapper.countContractBySupplier(tenantId, CONTRACT_PENDING_SUPPLIER))
                .contractPendingConfirmSupplier(statsMapper.countContractBySupplier(tenantId, CONTRACT_PENDING_CONFIRM))
                .pendingQuote(statsMapper.countPendingQuote(tenantId))
                .awardBySupplier(statsMapper.countAwardBySupplier(tenantId))
                .build();
    }
}
