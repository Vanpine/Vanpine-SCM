package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierDetailDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.AiSupplierSummaryDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.supplier.vo.EnterpriseProfileVO;

import java.util.List;
import java.util.Map;

/**
 * 企业画像查询服务接口 — 我的画像 / 公开画像 / 供应商广场列表 / 内部契约
 * <p>
 * 只读查询，无 @Transactional。列表关联数据（尽调/对接状态）采用批量查询，消 N+1。
 * 返回一律 VO（前端展示），不向控制器暴露实体，避免控制器耦合业务逻辑。
 *
 * @author gsc
 */
public interface ProfileQueryService {

    /**
     * 获取当前租户的企业画像（超管 tenantId=1 返回合成画像，前端入口用）
     *
     * @param tenantId 租户ID
     * @return 企业画像VO
     */
    EnterpriseProfileVO getMyProfile(Long tenantId);

    /**
     * 查对方企业画像+尽调信息（对接记录/需求广场查看；超管无画像抛业务异常）
     *
     * @param tenantId 对方租户ID
     * @return 企业画像VO（含尽调信息）
     */
    EnterpriseProfileVO getPublicProfile(Long tenantId);

    /**
     * 分页查供应商列表（需求广场"找供应商"，支持关键词搜索）
     *
     * @param keyword  关键词（企业名/行业/地区/品类）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    Map<String, Object> listSuppliers(String keyword, int pageNum, int pageSize);

    // ==================== 内部契约（平台/交易/交付域 Feign 调用） ====================

    /** 按租户ID查画像信息契约 DTO（不存在返回 null） */
    ProfileInfoDTO getInfoByTenantId(Long tenantId);

    /** 批量按租户ID查画像契约 DTO（tenantId → DTO，不存在的不在 map 中） */
    Map<Long, ProfileInfoDTO> getInfoByTenantIds(List<Long> tenantIds);

    // ==================== 内部契约（AI 域工具查询） ====================

    /**
     * 按关键词搜索供应商摘要（AI 工具 search_suppliers 专用）
     * <p>
     * 复用供应商广场的搜索逻辑，额外在 Service 内拼接入住状态 accessStatus。
     *
     * @param keyword  关键词（企业名/行业/地区/品类）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 供应商摘要列表（无则空列表）
     */
    List<AiSupplierSummaryDTO> searchForAi(String keyword, int page, int pageSize);

    /**
     * 按租户ID查供应商详情（AI 工具 get_supplier_detail 专用）
     * <p>
     * 含资质认证、尽调明细与拼好的准入状态；不存在返回 null。
     *
     * @param tenantId 供应商租户ID
     * @return 供应商详情 DTO（不存在返回 null）
     */
    AiSupplierDetailDTO getDetailForAi(Long tenantId);
}
