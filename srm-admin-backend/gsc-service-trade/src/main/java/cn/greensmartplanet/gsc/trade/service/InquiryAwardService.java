package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.dto.AwardItemDTO;

import java.util.List;
import java.util.Map;

/**
 * 询盘授标服务接口 — 分量授标 / 主动完成授标
 * <p>
 * 授标规则（接口与实现一致）：
 * 只按采购数量判断是否结束，与需求「目标供应商数」无关；
 * 分配量凑满需求量 → awarded；未满 → 保持 quoting/expired，可继续授标。
 *
 * @author gsc
 */
public interface InquiryAwardService {

    /**
     * 分量授标：校验报价/供应能力/总量不超采购量，标记中标，凑满需求量才翻已授标
     *
     * @return completed=是否已满量结束, remaining=剩余未分配数量
     */
    Map<String, Object> award(Long id, Long tenantId, Long userId, List<AwardItemDTO> awards, String awardReason);

    /**
     * 主动完成授标（数量未满也结束，quoting/expired → awarded）
     */
    void completeAward(Long id, Long tenantId, Long userId);
}
