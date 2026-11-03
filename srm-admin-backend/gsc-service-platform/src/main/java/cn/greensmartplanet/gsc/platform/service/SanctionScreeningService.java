package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.vo.SanctionScreenResultVO;

/**
 * 制裁筛查服务 — 拿供应商公司名/注册号匹配本地名单库
 *
 * @author gsc
 */
public interface SanctionScreeningService {

    /**
     * 对入驻申请做筛查，命中落库并返回结果。
     * <p>
     * 幂等：同一申请重复筛查覆盖最新一条记录。
     * 名单库为空时返回 status=pending（不阻断审核）。
     *
     * @param applicationId 入驻申请ID
     */
    SanctionScreenResultVO screen(Long applicationId);
}
