package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.supplier.dto.EditProfileDTO;

/**
 * 企业画像生命周期服务接口 — 初始化 / 更新 / 履约回写 / 启动评分重算
 * <p>
 * 写操作统一走本服务（更新后经 ProfileSyncService 异步同步 AI 向量库）。
 * 入参用 DTO（前端编辑）+ 租户ID，不在控制器做实体装配，避免控制器耦合业务逻辑。
 *
 * @author gsc
 */
public interface ProfileLifecycleService {

    /** 从入驻申请初始化企业画像：申请身份→画像类型，联系信息优先尽调表，upsert 后同步 AI 向量库 */
    void initFromApplication(Long applicationId);

    /** 按编辑 DTO 更新企业画像（超管跳过；profileType 不可编辑，由入驻身份决定），更新后同步 AI 向量库 */
    void updateFromDto(Long tenantId, EditProfileDTO dto);

    /** 交付域回写履约表现：仅更新 5 个履约字段（交付终态触发） */
    void updatePerformance(PerformanceUpdateDTO dto);
}
