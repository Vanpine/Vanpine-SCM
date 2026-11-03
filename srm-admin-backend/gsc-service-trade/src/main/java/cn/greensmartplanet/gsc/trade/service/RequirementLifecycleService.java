package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.dto.PublishRequirementDTO;
import cn.greensmartplanet.gsc.trade.vo.RequirementVO;

/**
 * 采购需求生命周期服务接口 — 发布 / 编辑 / 重新发布 / 关闭
 * <p>
 * 状态机（RequirementStatus）：open → closed / expired / completed；
 * closed/expired 可重新发布 → open；completed 已闭环不可回退。
 *
 * @author gsc
 */
public interface RequirementLifecycleService {

    /**
     * 发布采购需求：校验采购方画像（buyer/both），入库并返回 VO
     */
    RequirementVO publish(Long tenantId, PublishRequirementDTO dto);

    /**
     * 编辑需求：仅本人 open 需求可编辑，同步校验目标供应商数达标自动翻 completed
     */
    void update(Long id, Long tenantId, PublishRequirementDTO dto);

    /**
     * 重新发布已关闭/过期需求：先翻 open 再更新字段，完成后重查是否达标
     */
    void republish(Long id, Long tenantId, PublishRequirementDTO dto);

    /**
     * 关闭需求：仅本人 open/completed 可关闭
     */
    void close(Long id, Long tenantId);
}
