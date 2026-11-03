package cn.greensmartplanet.gsc.platform.service;

import cn.greensmartplanet.gsc.platform.vo.SanctionSyncStatusVO;

/**
 * 制裁名单同步服务 — 定时/手动拉取 OpenSanctions 名单入库
 *
 * @author gsc
 */
public interface SanctionSyncService {

    /**
     * 异步触发一次同步（立即返回，后台下载+解析+入库）。
     * <p>
     * 同步中重复触发会被忽略（running 标志防并发）。结果通过 getStatus() 查询。
     */
    void triggerSync();

    /**
     * 最近同步状态（超管名单库页顶部展示；前端同步后轮询此接口）
     */
    SanctionSyncStatusVO getStatus();
}
