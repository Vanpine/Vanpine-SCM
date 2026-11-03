package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryUpdateRequest;
import cn.greensmartplanet.gsc.trade.dto.CreateInquiryDTO;
import cn.greensmartplanet.gsc.trade.vo.InquiryVO;

import java.time.LocalDateTime;

/**
 * 询盘生命周期服务接口 — 创建 / 编辑 / 关闭 / 取消 / 延长报价截止
 * <p>
 * 生命周期状态机：open → quoting → awarded / closed / expired / cancelled。
 * 创建询盘时批量邀请供应商（委托 InquiryInviteService.batchInvite）。
 *
 * @author gsc
 */
public interface InquiryLifecycleService {

    /**
     * 创建询盘 + 受邀供应商（初始状态 open）
     */
    InquiryVO create(Long tenantId, Long userId, CreateInquiryDTO dto);

    /**
     * 编辑询盘（仅 open 状态且无供应商接受邀请）
     */
    void update(Long id, Long tenantId, Long userId, CreateInquiryDTO dto);

    /**
     * 部分更新询盘字段（AI 工具 update_inquiry 用）
     * <p>
     * 与 update 的区别：只 set 非 null 字段（防模型只改交期却清空其他字段）。
     * 校验与 update 一致：仅采购方、open 状态、无供应商接受邀请。
     */
    void updateForAi(AiInquiryUpdateRequest request);

    /**
     * 关闭询盘（open/quoting/expired；已有授标不可关，须走完成授标）
     */
    void close(Long id, Long tenantId, Long userId);

    /**
     * 取消询盘（仅 open，尚无报价；已邀请/已接受均可取消）
     */
    void cancel(Long id, Long tenantId, Long userId);

    /**
     * 延长报价截止时间（open/quoting/expired；已过期延长后：有报价恢复 quoting，无报价恢复 open）
     */
    void extendDeadline(Long inquiryId, Long tenantId, Long userId, LocalDateTime newDeadline);
}
