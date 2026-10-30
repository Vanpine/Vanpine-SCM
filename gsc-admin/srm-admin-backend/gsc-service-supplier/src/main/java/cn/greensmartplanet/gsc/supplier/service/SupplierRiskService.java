package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierRiskDTO;

/**
 * 供应商风险评分服务接口 — 三维健康分（交付/质量/合规），供 AI 工具 risk_assess_supplier 调用
 * <p>
 * 只读查询，无 @Transactional。评分算法内聚在供应商域（原始数据都在本域两张表，
 * 无跨服务往返），AI 域经 Feign 透传结果，Python 侧只负责转自然语言。
 *
 * @author gsc
 */
public interface SupplierRiskService {

    /**
     * 按租户ID评估供应商风险（不存在返回 null）
     * <p>
     * 三维：交付（准时率）、质量（验收合格率+退货）、合规（准入状态）。
     * 语义统一为健康分——分数越高越安全。履约无样本时交付/质量给中性 60 分并标记样本不足。
     *
     * @param tenantId 供应商租户ID
     * @return 风险评分 DTO（不存在返回 null）
     */
    AiSupplierRiskDTO assess(Long tenantId);
}
