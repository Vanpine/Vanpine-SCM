package cn.greensmartplanet.gsc.supplier.service.impl;


import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.util.CurrencyAmountValidator;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.supplier.dto.UpdateDdInfoDTO;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligenceAttachment;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceAttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.common.enums.DdFirmStatus;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.service.DueDiligenceService;
import cn.greensmartplanet.gsc.supplier.vo.DueDiligenceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 尽职调查服务实现
 * <p>
 * 负责尽调记录的查/改/支付/报告上传，不涉及事务所分配和审核（那些在平台域 ApplicationServiceImpl 中）。
 * <p>
 * 核心设计：due_diligence 与 application 表有大量重叠字段（公司名、联系人等），
 * 查询时如果 dd 某字段为空，自动从申请信息取原始值兜底（fillFromApplication）。
 * <p>
 * ⚠️ 微服务拆分：application 表归属平台域，重叠字段兜底经 Feign
 * （ApplicationClient）查询，替代跨服务直查 application 表。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DueDiligenceServiceImpl implements DueDiligenceService {

    /** 尽调记录 Mapper（due_diligence 表） */
    private final DueDiligenceMapper dueDiligenceMapper;
    /** 平台域 Feign：入驻申请信息（application 表归属平台域，重叠字段兜底用） */
    private final ApplicationClient applicationClient;
    /** OSS 服务：组装 VO 时按 oss_key 现签附件 URL */
    private final OssService ossService;
    /** 尽调附件子表 Mapper（多值附件） */
    private final DueDiligenceAttachmentMapper attachmentMapper;

    /**
     * 按入驻申请ID查询尽调记录（含重叠字段兜底）
     * <p>
     * 1. 先查 due_diligence 表
     * 2. 如果重叠字段有 null，从 application 表取原始值兜底
     * 3. Entity → VO 返回给前端
     *
     * @param applicationId 入驻申请ID
     * @return 尽调展示VO
     * @throws BizException 尽调记录不存在时抛 DUE_DILIGENCE_NOT_FOUND
     */
    @Override
    public DueDiligenceVO getByApplicationId(Long applicationId) {
        // 1. 查尽调记录（1:1 关联 application）
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null) {
            throw new BizException(ErrorCode.DUE_DILIGENCE_NOT_FOUND);
        }

        // 2. 重叠字段兜底：due_diligence 为空时从 application 取原始值
        fillFromApplication(dd);

        // 3. 查多值附件子表 + Entity → VO 返回（附件按 oss_key 现签 URL）
        List<DueDiligenceAttachment> attachments = attachmentMapper.selectList(
                new LambdaQueryWrapper<DueDiligenceAttachment>()
                        .eq(DueDiligenceAttachment::getApplicationId, applicationId)
                        .orderByAsc(DueDiligenceAttachment::getSortOrder));
        return DueDiligenceVO.fromEntity(dd, attachments, ossService);
    }

    /**
     * 重叠字段兜底：due_diligence 表某字段为 null 时，从 application 表取原始值填充
     * <p>
     * 设计背景：审核通过时会把 application 的重叠字段拷贝到 due_diligence 作为初始值，
     * 但历史数据可能没拷贝，且用户可能只修改部分字段。这里做惰性查询兜底——只有确实需要时才查 application。
     *
     * @param dd 尽调实体（可能部分重叠字段为 null）
     */
    private void fillFromApplication(DueDiligence dd) {
        // 先快速判断是否有任何一个重叠字段为 null，大多数情况下所有字段都有值，跳过查询
        boolean needFallback = dd.getCompanyName() == null || dd.getCountry() == null
                || dd.getRegNumber() == null || dd.getContactName() == null
                || dd.getContactEmail() == null || dd.getContactPhone() == null
                || dd.getBuyVolumeAmount() == null || dd.getBuyVolumeCurrency() == null
                || dd.getFiscalPeriodStart() == null || dd.getFiscalPeriodEnd() == null
                || dd.getApplicantRole() == null;
        if (!needFallback) return; // 所有字段都有值，无需兜底

        // 惰性查申请信息（只在实际需要时才查，避免无意义的调用）
        // ⚠️ 微服务拆分：application 表归属平台域，经 Feign 查询（降级抛异常不静默通过）
        ApplicationInfoDTO app = applicationClient.getById(dd.getApplicationId()).getData();
        if (app == null) return; // application 不存在（极端情况，数据异常）

        // 逐字段判空兜底：dd 为 null → 用 app 的值
        // ---- 基础信息 ----
        if (dd.getCompanyName() == null) dd.setCompanyName(app.getCompanyName());
        if (dd.getCountry() == null) dd.setCountry(app.getCountry());
        if (dd.getRegNumber() == null) dd.setRegNumber(app.getRegNumber());
        if (dd.getEstablishedYear() == null) dd.setEstablishedYear(app.getEstablishedYear());
        // ---- 联系信息 ----
        if (dd.getContactName() == null) dd.setContactName(app.getContactName());
        if (dd.getContactTitle() == null) dd.setContactTitle(app.getContactTitle());
        if (dd.getContactEmail() == null) dd.setContactEmail(app.getContactEmail());
        if (dd.getContactPhone() == null) dd.setContactPhone(app.getContactPhone());
        // ---- 采购方信息 ----
        if (dd.getBuyCategory() == null) dd.setBuyCategory(app.getBuyCategory());
        if (dd.getBuyVolume() == null) dd.setBuyVolume(app.getBuyVolume());
        if (dd.getBuyVolumeAmount() == null) dd.setBuyVolumeAmount(app.getBuyVolumeAmount());
        if (dd.getBuyVolumeCurrency() == null) dd.setBuyVolumeCurrency(app.getBuyVolumeCurrency());
        if (dd.getFiscalPeriodStart() == null) dd.setFiscalPeriodStart(app.getFiscalPeriodStart());
        if (dd.getFiscalPeriodEnd() == null) dd.setFiscalPeriodEnd(app.getFiscalPeriodEnd());
        if (dd.getBuyMarket() == null) dd.setBuyMarket(app.getBuyMarket());
        // ---- 供应方信息 ----
        if (dd.getSupCategory() == null) dd.setSupCategory(app.getSupCategory());
        if (dd.getSupCapacity() == null) dd.setSupCapacity(app.getSupCapacity());
        if (dd.getSupCerts() == null) dd.setSupCerts(app.getSupCerts());
        if (dd.getSupExport() == null) dd.setSupExport(app.getSupExport());
        // ---- 申请身份（null 或空字符串都走兜底） ----
        if (dd.getApplicantRole() == null || dd.getApplicantRole().isBlank()) dd.setApplicantRole(app.getApplicantRole());
    }

    /**
     * 保存或更新尽调信息（用户在尽调表单填写后点击保存）
     * <p>
     * 逻辑：查是否存在 → 存在则 UPDATE，不存在则 INSERT。
     * 注意：这里是用户修改自己的尽调信息（省份、城市、资质文件等），不涉及事务所分配。
     * <p>
     * ⚠️ 更新采用「非 null 覆盖」而非整行覆盖：
     * DueDiligenceMapper.update 的 SQL 是无条件 SET 全列，若直接把 DTO 转成的实体丢进去，
     * 前端漏传的字段（或字段名对不上被 Jackson 丢弃的字段）会被静默写成 NULL，
     * 造成已有附件 key 丢失。因此这里先取库中现有记录，逐字段用 DTO 的非 null 值覆盖后再更新。
     * <p>
     * 多值附件：DTO.attachments 为 null 表示本次不改动；为空数组表示清空；
     * 否则整体替换式保存（先物理删旧、再按顺序插入新）。
     *
     * @param applicationId 入驻申请ID
     * @param dto           前端提交的尽调扩展信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(Long applicationId, UpdateDdInfoDTO dto) {
        validateAmountFields(dto);

        // 查是否已有尽调记录
        DueDiligence existing = dueDiligenceMapper.selectByApplicationId(applicationId);

        // 以库中现有记录为基准做非 null 覆盖，避免漏传字段被写成 NULL
        DueDiligence dd = existing != null ? existing : new DueDiligence();
        mergeNonNull(dd, dto);
        dd.setApplicationId(applicationId); // 确保关联关系正确

        if (existing != null) {
            dueDiligenceMapper.update(dd);
        } else {
            // 无记录 → 走 INSERT（极端情况：审核通过后尽调记录被删了）
            dueDiligenceMapper.insert(dd);
        }

        // 多值附件：null 不动、空数组清空、有值整体替换
        if (dto.getAttachments() != null) {
            replaceAttachments(applicationId, dto.getAttachments());
        }

        log.info("尽调信息已保存, applicationId={}, 附件={}", applicationId,
                dto.getAttachments() == null ? "未改动" : dto.getAttachments().size() + "项");
    }

    /**
     * 用 DTO 中的非 null 字段覆盖实体（null 表示「本次不修改该字段」，保留库中原值）。
     *
     * @param dd  目标实体（库中现有记录或新建）
     * @param dto 前端提交的扩展信息
     */
    private void mergeNonNull(DueDiligence dd, UpdateDdInfoDTO dto) {
        // ---- 基础信息 ----
        if (dto.getCompanyName() != null) dd.setCompanyName(dto.getCompanyName());
        if (dto.getCountry() != null) dd.setCountry(dto.getCountry());
        if (dto.getRegNumber() != null) dd.setRegNumber(dto.getRegNumber());
        if (dto.getEstablishedYear() != null) dd.setEstablishedYear(dto.getEstablishedYear());
        if (dto.getRegisteredAddress() != null) dd.setRegisteredAddress(dto.getRegisteredAddress());
        // ---- 联系信息 ----
        if (dto.getContactName() != null) dd.setContactName(dto.getContactName());
        if (dto.getContactTitle() != null) dd.setContactTitle(dto.getContactTitle());
        if (dto.getContactEmail() != null) dd.setContactEmail(dto.getContactEmail());
        if (dto.getContactPhone() != null) dd.setContactPhone(dto.getContactPhone());
        // ---- 采购方 / 供应方 ----
        if (dto.getBuyCategory() != null) dd.setBuyCategory(dto.getBuyCategory());
        if (dto.getBuyVolume() != null) dd.setBuyVolume(dto.getBuyVolume());
        if (dto.getBuyVolumeAmount() != null) dd.setBuyVolumeAmount(dto.getBuyVolumeAmount());
        if (dto.getBuyVolumeCurrency() != null) dd.setBuyVolumeCurrency(dto.getBuyVolumeCurrency());
        if (dto.getFiscalPeriodStart() != null) dd.setFiscalPeriodStart(dto.getFiscalPeriodStart());
        if (dto.getFiscalPeriodEnd() != null) dd.setFiscalPeriodEnd(dto.getFiscalPeriodEnd());
        if (dto.getBuyMarket() != null) dd.setBuyMarket(dto.getBuyMarket());
        if (dto.getSupCategory() != null) dd.setSupCategory(dto.getSupCategory());
        if (dto.getSupCapacity() != null) dd.setSupCapacity(dto.getSupCapacity());
        if (dto.getSupCerts() != null) dd.setSupCerts(dto.getSupCerts());
        if (dto.getSupExport() != null) dd.setSupExport(dto.getSupExport());
        // ---- 企业补充信息 ----
        if (dto.getProvince() != null) dd.setProvince(dto.getProvince());
        if (dto.getCity() != null) dd.setCity(dto.getCity());
        if (dto.getBusinessLicenseKey() != null) dd.setBusinessLicenseKey(dto.getBusinessLicenseKey());
        if (dto.getWebsite() != null) dd.setWebsite(dto.getWebsite());
        if (dto.getDescription() != null) dd.setDescription(dto.getDescription());
        if (dto.getEmployeeCount() != null) dd.setEmployeeCount(dto.getEmployeeCount());
        if (dto.getAnnualRevenue() != null) dd.setAnnualRevenue(dto.getAnnualRevenue());
        if (dto.getAnnualRevenueAmount() != null) dd.setAnnualRevenueAmount(dto.getAnnualRevenueAmount());
        if (dto.getAnnualRevenueCurrency() != null) dd.setAnnualRevenueCurrency(dto.getAnnualRevenueCurrency());
        if (dto.getFiscalPeriodStart() != null) dd.setFiscalPeriodStart(dto.getFiscalPeriodStart());
        if (dto.getFiscalPeriodEnd() != null) dd.setFiscalPeriodEnd(dto.getFiscalPeriodEnd());
        // ---- 供应方补充 ----
        if (dto.getMainProducts() != null) dd.setMainProducts(dto.getMainProducts());
        if (dto.getManagementCert() != null) dd.setManagementCert(dto.getManagementCert());
        if (dto.getProductCert() != null) dd.setProductCert(dto.getProductCert());
        if (dto.getKeyClients() != null) dd.setKeyClients(dto.getKeyClients());
        if (dto.getDeliveryTerms() != null) dd.setDeliveryTerms(dto.getDeliveryTerms());
        if (dto.getLeadTime() != null) dd.setLeadTime(dto.getLeadTime());
        if (dto.getExportMarkets() != null) dd.setExportMarkets(dto.getExportMarkets());
        // ---- 采购方补充 ----
        if (dto.getIndustry() != null) dd.setIndustry(dto.getIndustry());
        if (dto.getProcurementDetail() != null) dd.setProcurementDetail(dto.getProcurementDetail());
        if (dto.getSupplierRequirement() != null) dd.setSupplierRequirement(dto.getSupplierRequirement());
        if (dto.getTargetRegion() != null) dd.setTargetRegion(dto.getTargetRegion());
        if (dto.getPaymentTerms() != null) dd.setPaymentTerms(dto.getPaymentTerms());
        // ---- 尽调核心材料（附件一律存 key） ----
        if (dto.getLegalPersonName() != null) dd.setLegalPersonName(dto.getLegalPersonName());
        if (dto.getLegalPersonId() != null) dd.setLegalPersonId(dto.getLegalPersonId());
        if (dto.getShareholderInfoKey() != null) dd.setShareholderInfoKey(dto.getShareholderInfoKey());
        if (dto.getFinancialReportKey() != null) dd.setFinancialReportKey(dto.getFinancialReportKey());
        if (dto.getLegalRecordText() != null) dd.setLegalRecordText(dto.getLegalRecordText());
        if (dto.getLegalRecordKey() != null) dd.setLegalRecordKey(dto.getLegalRecordKey());
    }

    private void validateAmountFields(UpdateDdInfoDTO dto) {
        CurrencyAmountValidator.getAmountError("年采购额", dto.getBuyVolumeAmount(), dto.getBuyVolumeCurrency(),
                        dto.getFiscalPeriodStart(), dto.getFiscalPeriodEnd())
                .ifPresent(error -> {
                    throw new BizException(ErrorCode.BAD_REQUEST, error);
                });
        CurrencyAmountValidator.getAmountError("年营收", dto.getAnnualRevenueAmount(), dto.getAnnualRevenueCurrency(),
                        dto.getFiscalPeriodStart(), dto.getFiscalPeriodEnd())
                .ifPresent(error -> {
                    throw new BizException(ErrorCode.BAD_REQUEST, error);
                });
    }

    /**
     * 整体替换式保存多值附件：先物理删除该申请下所有旧附件，再按提交顺序插入新附件。
     * <p>
     * 本表无逻辑删除，增删即物理操作；列表为空即清空全部。
     *
     * @param applicationId 入驻申请ID
     * @param items         前端提交的附件列表（ossKey + fileName）
     */
    private void replaceAttachments(Long applicationId, List<UpdateDdInfoDTO.AttachmentItem> items) {
        // 1. 物理删除旧附件
        attachmentMapper.delete(new LambdaQueryWrapper<DueDiligenceAttachment>()
                .eq(DueDiligenceAttachment::getApplicationId, applicationId));

        // 2. 按提交顺序插入新附件（跳过无 ossKey 的无效项）
        int sortOrder = 0;
        for (UpdateDdInfoDTO.AttachmentItem item : items) {
            if (item == null || item.getOssKey() == null || item.getOssKey().isBlank()) {
                continue;
            }
            DueDiligenceAttachment attachment = new DueDiligenceAttachment();
            attachment.setApplicationId(applicationId);
            attachment.setOssKey(item.getOssKey());
            attachment.setFileName(item.getFileName());
            attachment.setSortOrder(sortOrder++);
            attachmentMapper.insert(attachment);
        }
    }

    /**
     * 上传尽调报告（管理员上传最终尽调报告文件）
     * <p>
     * 前置校验：尽调记录存在 + 已支付 + 事务所已提交结果（completed）。
     * 只有事务所完成核查提交后，管理员才能上传最终报告。
     *
     * @param applicationId 入驻申请ID
     * @param ddReportUrl   报告文件OSS地址
     * @param ddReportName  报告文件原始名称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadReport(Long applicationId, String ddReportUrl, String ddReportName) {
        // 1. 校验尽调记录存在
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "尽调记录不存在");
        }

        // 2. 校验已支付（不支付的申请不应该上传最终报告）
        if (!"paid".equals(dd.getPaymentStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先完成套餐支付");
        }

        // 3. 校验事务所已提交结果（只有事务所完成后管理员才能上传最终报告）
        if (!DdFirmStatus.COMPLETED.getCode().equals(dd.getDdFirmStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请等待事务所完成核查提交后再上传最终报告");
        }

        // 4. 更新报告信息，Mapper SQL 会自动设置 dd_report_status = 'completed' 和 dd_report_time = NOW()
        dueDiligenceMapper.updateDdReport(applicationId, ddReportUrl, ddReportName);
        log.info("尽调报告已上传, applicationId={}", applicationId);
    }
}
