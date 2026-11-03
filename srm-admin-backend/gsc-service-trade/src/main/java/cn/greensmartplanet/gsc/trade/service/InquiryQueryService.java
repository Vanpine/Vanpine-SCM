package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.api.trade.dto.AiInquiryQuery;
import cn.greensmartplanet.gsc.api.trade.dto.AiInquirySummaryDTO;
import cn.greensmartplanet.gsc.trade.vo.InquiryVO;

import java.util.List;
import java.util.Map;

/**
 * 询盘查询服务接口 — 列表 / 详情 / 供应商ID集合 / 对接活跃状态 / 内部契约
 * <p>
 * 只读查询归口本服务，与写操作（生命周期/邀请/授标/AI 执行）解耦。
 *
 * @author gsc
 */
public interface InquiryQueryService {

    /**
     * 分页列表（采购商看自己 / 供应商看受邀），批量填充受邀/报价/授标统计
     */
    Map<String, Object> list(Long tenantId, int pageNum, int pageSize,
                             String status, String keyword, String role);

    /**
     * 询盘详情（含受邀名单 + 报价列表）
     */
    InquiryVO getDetail(Long id, Long tenantId, String role);

    /**
     * 查当前采购商在该需求下已授标过的供应商租户ID（前端"已授标"集合，禁选用）
     *
     * @param tenantId      采购商租户ID
     * @param requirementId 采购需求ID（为空则返回空集合，不启用授标禁选）
     */
    List<Long> getInvitedSupplierIds(Long tenantId, Long requirementId);

    /**
     * 查该需求下进行中询盘（open/quoting）已邀请的供应商租户ID（前端"询价中"集合，禁选用）
     *
     * @param tenantId      采购商租户ID
     * @param requirementId 采购需求ID（为空则返回空集合）
     */
    List<Long> getActiveInquirySupplierIds(Long tenantId, Long requirementId);

    /**
     * 批量查哪些对接已有活跃询盘，返回 Map<connectionId, status>（status: open/quoting/awarded）
     */
    Map<Long, String> getActiveInquiryMap(Long tenantId, List<Long> connectionIds);

    /**
     * 按需求ID查拒绝过相关询盘的供应商租户ID集合（内部契约，供应商域智能匹配页"已拒绝该询盘"标注）
     */
    List<Long> getDeclinedSupplierIds(Long requirementId, Long buyerTenantId);

    /**
     * 按当前租户分页查询盘摘要（AI 工具 list_inquiries 专用）
     * <p>
     * 复用 list 的 buyer/supplier 双视角查询逻辑，返回 AI 友好的摘要字段子集。
     * tenantId 由调用方（AI 域）从登录态注入，禁止由模型传值（防越权，红线 2）。
     *
     * @param query 查询条件（tenantId 必填，status/keyword/role/page/pageSize 可空）
     * @return 询盘摘要列表（无则空列表）
     */
    List<AiInquirySummaryDTO> listForAi(AiInquiryQuery query);

    /**
     * 按询盘ID查单条摘要（AI 域拼确认文案用）
     * <p>
     * 归属校验：非本人发起/受邀返回 null（AI 域据此拒绝提案）。
     *
     * @param query 含 tenantId + inquiryId
     * @return 询盘摘要，不存在/非本人返回 null
     */
    AiInquirySummaryDTO getForAi(AiInquiryQuery query);
}
