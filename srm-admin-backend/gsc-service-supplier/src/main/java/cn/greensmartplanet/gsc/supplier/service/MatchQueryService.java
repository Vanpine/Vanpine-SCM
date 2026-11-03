package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.common.result.PageResult;
import cn.greensmartplanet.gsc.supplier.vo.SupplierRecommendVO;

import java.util.List;

/**
 * 智能匹配查询服务接口 — 匹配候选 / 询盘拒绝标注
 * <p>
 * 只读查询。需求字段经 RequirementClient 从交易域取（Feign），匹配策略仍在本域（MatchStrategy）；
 * 询盘拒绝标注经 InquiryClient 查交易域。归属/权限校验下沉到本层，Controller 保持薄壳。
 *
 * @author gsc
 */
public interface MatchQueryService {

    /**
     * 智能匹配候选供应商（校验需求存在 + 归属权限后执行匹配策略）
     *
     * @param requirementId 需求ID
     * @param tenantId      当前租户ID（权限校验：仅需求发布方或超管可调用）
     * @return 匹配候选供应商列表；reasonStatus=generating 时 AI 理由仍在后台写，前端应轮询
     */
    List<SupplierRecommendVO> getMatchedSuppliers(Long requirementId, Long tenantId);

    /**
     * 查拒绝过该需求相关询盘的供应商（智能匹配页"已拒绝该询盘"标注，校验需求存在 + 归属权限）
     *
     * @param requirementId 需求ID
     * @param tenantId      当前租户ID（权限校验：仅需求发布方或超管可调用）
     * @return 拒绝过相关询盘的供应商租户ID集合
     */
    List<Long> getDeclinedSupplierIds(Long requirementId, Long tenantId);

    /**
     * 需求广场「找供应商」智能推荐：按指定需求匹配并分页
     * <p>
     * 复用 {@link #getMatchedSuppliers} 的匹配引擎与 Redis 缓存；对接状态现查不进缓存。
     * 仅进行中的需求可推荐，避免对已关闭/过期需求空跑。
     *
     * @param requirementId 采购需求ID（必须属于当前租户，或超管）
     * @param tenantId      当前租户ID
     * @param keyword       可选，对已排序结果二次过滤（企业名/品类/行业/地区）
     * @param pageNum       页码（从 1 起）
     * @param pageSize      每页条数
     * @return 分页推荐结果（匹配分 + 对接状态；不含理由）
     */
    PageResult<SupplierRecommendVO> recommendForPlaza(Long requirementId, Long tenantId,
                                                      String keyword, int pageNum, int pageSize);
}
