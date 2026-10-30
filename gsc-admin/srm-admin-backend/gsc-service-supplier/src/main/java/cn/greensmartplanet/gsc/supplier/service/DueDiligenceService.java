package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.supplier.dto.UpdateDdInfoDTO;
import cn.greensmartplanet.gsc.supplier.vo.DueDiligenceVO;

/**
 * 尽职调查服务接口
 * <p>
 * 负责尽调记录的简单 CRUD 操作（查/改/支付/报告上传）。
 * 涉及多表协调的场景（审核、分配事务所、核查清单）在 ApplicationService 中。
 *
 * @author gsc
 */
public interface DueDiligenceService {

    /**
     * 按入驻申请ID查尽调记录（含重叠字段从 application 表兜底）
     *
     * @param applicationId 入驻申请ID
     * @return 尽调展示VO，不存在返回 null
     */
    DueDiligenceVO getByApplicationId(Long applicationId);

    /**
     * 保存或更新尽调信息（用户在尽调表单修改后点击保存）
     * <p>
     * 有则更新（UPDATE），无则新增（INSERT）。
     * <p>
     * 更新语义：先取库中现有记录，仅用 DTO 中**非 null** 的字段覆盖，
     * 避免前端漏传某字段导致该列被静默写成 NULL（历史缺陷）。
     * 多值附件传 null 表示不改动、传空数组表示清空，否则整体替换式保存。
     *
     * @param applicationId 入驻申请ID
     * @param dto           前端提交的尽调扩展信息
     */
    void saveOrUpdate(Long applicationId, UpdateDdInfoDTO dto);

    /**
     * 上传尽调报告 — 更新报告 URL 和文件名，状态自动设为 completed
     *
     * @param applicationId 入驻申请ID
     * @param ddReportUrl   报告文件OSS地址
     * @param ddReportName  报告文件原始名称
     */
    void uploadReport(Long applicationId, String ddReportUrl, String ddReportName);
}
