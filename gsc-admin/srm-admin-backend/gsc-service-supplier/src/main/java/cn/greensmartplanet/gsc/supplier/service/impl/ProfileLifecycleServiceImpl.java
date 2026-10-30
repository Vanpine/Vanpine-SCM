package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.util.CurrencyAmountValidator;
import cn.greensmartplanet.gsc.supplier.dto.EditProfileDTO;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.supplier.entity.EnterpriseProfile;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.mapper.EnterpriseProfileMapper;
import cn.greensmartplanet.gsc.supplier.service.ProfileLifecycleService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 企业画像生命周期服务实现 — 初始化 / 更新 / 履约回写 / 启动评分重算
 * <p>
 * ⚠️ 微服务拆分：application 表归属平台域，画像初始化/完整度评分的
 * 申请字段经 Feign（ApplicationClient）查询，替代跨服务直查 application 表。
 * 更新后经 {@link ProfileSyncService} 异步同步 AI 向量库（失败不影响主流程）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileLifecycleServiceImpl implements ProfileLifecycleService {

    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final ApplicationClient applicationClient;
    private final DueDiligenceMapper dueDiligenceMapper;
    private final ProfileSyncService profileSyncService; // AI 向量库同步服务

    /** 启动完整度重算开关（默认关）——⚠️ 与 platform 版对齐，防止多实例/混跑期重复重算 */
    @Value("${gsc.profile-sync.recalc-on-startup:false}")
    private boolean recalcOnStartup;

    /**
     * 启动时重算所有已有画像的完整度（按身份类型动态算法）
     * 幂等：分数不变不更新
     * <p>
     * 开关：{@code gsc.profile-sync.recalc-on-startup=false} 默认关闭，
     * 避免切流窗口内旧单体/多实例同时重算（幂等操作但浪费资源 + Feign 放大）。
     */
    @PostConstruct
    public void recalculateScoresOnStartup() {
        if (!recalcOnStartup) {
            log.info("画像完整度重算已关闭（gsc.profile-sync.recalc-on-startup=false），跳过启动重算");
            return;
        }
        try {
            List<EnterpriseProfile> all = enterpriseProfileMapper.selectList(null);
            if (all == null || all.isEmpty()) return;
            int updated = 0;
            for (EnterpriseProfile profile : all) {
                // ⚠️ Feign 查申请（降级抛异常，启动重算失败仅记日志不阻断启动）
                ApplicationInfoDTO app = applicationClient.getByTenantId(profile.getTenantId()).getData();
                if (app == null) continue;
                DueDiligence dd = dueDiligenceMapper.selectByApplicationId(app.getId());

                String contactName = app.getContactName();
                String contactPhone = app.getContactPhone();
                String contactEmail = app.getContactEmail();
                if (dd != null) {
                    if (dd.getContactName() != null) contactName = dd.getContactName();
                    if (dd.getContactPhone() != null) contactPhone = dd.getContactPhone();
                    if (dd.getContactEmail() != null) contactEmail = dd.getContactEmail();
                }

                int newScore = computeScore(app, dd, contactName, contactPhone, contactEmail, profile.getProfileType());
                Integer oldScore = profile.getCompletenessScore();
                if (oldScore == null || oldScore != newScore) {
                    profile.setCompletenessScore(newScore);
                    enterpriseProfileMapper.update(profile);
                    updated++;
                    log.info("画像完整度更新: tenantId={}, {}→{}", profile.getTenantId(), oldScore, newScore);
                }
            }
            if (updated > 0) {
                log.info("画像完整度批量重算完成，共更新 {} 条", updated);
            }
        } catch (Exception e) {
            log.error("画像完整度批量重算失败", e);
        }
    }

    /** 从入驻申请初始化企业画像：申请身份→画像类型，联系信息优先尽调表，upsert 后同步 AI 向量库 */
    @Override
    public void initFromApplication(Long applicationId) {
        // 1. 查入驻申请（⚠️ Feign 查平台域申请，降级抛异常不静默通过）
        ApplicationInfoDTO app = applicationClient.getById(applicationId).getData();
        if (app == null || app.getTenantId() == null) {
            log.warn("画像初始化跳过：申请不存在或未分配租户, applicationId={}", applicationId);
            return;
        }

        // 2. applicantRole → profileType
        String role = app.getApplicantRole();
        String profileType;
        if (role != null && role.contains(",")) {
            profileType = "both";
        } else if ("supplier".equals(role)) {
            profileType = "supplier";
        } else {
            profileType = "buyer";
        }

        // 3. 联系信息和省份优先取尽调表（企业填写的更新版本），其次取申请表
        String contactName = app.getContactName();
        String contactPhone = app.getContactPhone();
        String contactEmail = app.getContactEmail();
        String province = null;
        String city = null;
        String regNumber = app.getRegNumber();
        Integer establishedYear = app.getEstablishedYear();
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd != null) {
            if (dd.getContactName() != null) contactName = dd.getContactName();
            if (dd.getContactPhone() != null) contactPhone = dd.getContactPhone();
            if (dd.getContactEmail() != null) contactEmail = dd.getContactEmail();
            if (dd.getRegNumber() != null) regNumber = dd.getRegNumber();
            if (dd.getEstablishedYear() != null) establishedYear = dd.getEstablishedYear();
            province = dd.getProvince();
            city = dd.getCity();
        }

        // 4. 组装 EnterpriseProfile
        EnterpriseProfile profile = EnterpriseProfile.builder()
                .tenantId(app.getTenantId())
                .profileType(profileType)
                .companyName(app.getCompanyName())
                .country(app.getCountry())
                .regNumber(regNumber)
                .establishedYear(establishedYear)
                .province(province)
                .city(city)
                .buyCategories(app.getBuyCategory())
                .buyVolume(dd != null && dd.getBuyVolume() != null ? dd.getBuyVolume() : app.getBuyVolume())
                .buyVolumeAmount(dd != null && dd.getBuyVolumeAmount() != null ? dd.getBuyVolumeAmount() : app.getBuyVolumeAmount())
                .buyVolumeCurrency(dd != null && dd.getBuyVolumeCurrency() != null ? dd.getBuyVolumeCurrency() : app.getBuyVolumeCurrency())
                .fiscalPeriodStart(dd != null && dd.getFiscalPeriodStart() != null ? dd.getFiscalPeriodStart() : app.getFiscalPeriodStart())
                .fiscalPeriodEnd(dd != null && dd.getFiscalPeriodEnd() != null ? dd.getFiscalPeriodEnd() : app.getFiscalPeriodEnd())
                .buyMarket(app.getBuyMarket())
                .procurementDetail(dd != null ? dd.getProcurementDetail() : null)
                .supCategories(app.getSupCategory())
                .supCapacity(app.getSupCapacity())
                .supCerts(app.getSupCerts())
                .supExport(app.getSupExport())
                .mainProducts(dd != null ? dd.getMainProducts() : null)
                .industry(dd != null ? dd.getIndustry() : null)
                .keyClients(dd != null ? dd.getKeyClients() : null)
                .leadTime(dd != null ? dd.getLeadTime() : null)
                .employeeCount(dd != null ? dd.getEmployeeCount() : null)
                .annualRevenue(dd != null ? dd.getAnnualRevenue() : null)
                .annualRevenueAmount(dd != null ? dd.getAnnualRevenueAmount() : null)
                .annualRevenueCurrency(dd != null ? dd.getAnnualRevenueCurrency() : null)
                .description(dd != null ? dd.getDescription() : null)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .contactEmail(contactEmail)
                .legalRep(dd != null ? dd.getLegalPersonName() : null)
                .registeredAddress(dd != null ? dd.getRegisteredAddress() : null)
                .completenessScore(computeScore(app, dd, contactName, contactPhone, contactEmail, profileType))
                .build();

        // 5. upsert：已存在则更新，不存在则插入
        EnterpriseProfile existing = enterpriseProfileMapper.selectByTenantId(app.getTenantId());
        if (existing != null) {
            profile.setId(existing.getId());
            enterpriseProfileMapper.update(profile);
            log.info("企业画像已更新, tenantId={}, profileType={}", app.getTenantId(), profileType);
        } else {
            enterpriseProfileMapper.insert(profile);
            log.info("企业画像已初始化, tenantId={}, profileType={}", app.getTenantId(), profileType);
        }

        // 6. 同步到 AI 向量库（异步执行，失败不影响主流程）
        profileSyncService.syncProfile(profile);
    }

    /** 按编辑 DTO 更新企业画像（超管跳过；profileType 不可编辑，由入驻身份决定），更新后同步 AI 向量库 */
    @Override
    public void updateFromDto(Long tenantId, EditProfileDTO dto) {
        validateAmountFields(dto);

        // 1. 超管无需更新企业画像
        if (tenantId != null && tenantId == 1L) {
            return;
        }
        // 2. 查当前画像（查不到抛业务异常）
        EnterpriseProfile profile = enterpriseProfileMapper.selectByTenantId(tenantId);
        if (profile == null) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }
        // 3. 逐字段应用编辑（profileType 不可通过编辑修改，由入驻身份决定）
        profile.setCompanyName(dto.getCompanyName());
        profile.setCountry(dto.getCountry());
        profile.setProvince(dto.getProvince());
        profile.setCity(dto.getCity());
        profile.setBuyCategories(dto.getBuyCategories());
        profile.setBuyVolume(dto.getBuyVolume());
        profile.setBuyVolumeAmount(dto.getBuyVolumeAmount());
        profile.setBuyVolumeCurrency(dto.getBuyVolumeCurrency());
        profile.setFiscalPeriodStart(dto.getFiscalPeriodStart());
        profile.setFiscalPeriodEnd(dto.getFiscalPeriodEnd());
        profile.setBuyMarket(dto.getBuyMarket());
        profile.setSupCategories(dto.getSupCategories());
        profile.setSupCapacity(dto.getSupCapacity());
        profile.setSupCerts(dto.getSupCerts());
        profile.setSupExport(dto.getSupExport());
        profile.setMainProducts(dto.getMainProducts());
        profile.setIndustry(dto.getIndustry());
        profile.setKeyClients(dto.getKeyClients());
        profile.setLeadTime(dto.getLeadTime());
        profile.setEmployeeCount(dto.getEmployeeCount());
        profile.setAnnualRevenue(dto.getAnnualRevenue());
        profile.setAnnualRevenueAmount(dto.getAnnualRevenueAmount());
        profile.setAnnualRevenueCurrency(dto.getAnnualRevenueCurrency());
        profile.setFiscalPeriodStart(dto.getFiscalPeriodStart());
        profile.setFiscalPeriodEnd(dto.getFiscalPeriodEnd());
        profile.setDescription(dto.getDescription());
        profile.setContactName(dto.getContactName());
        profile.setContactPhone(dto.getContactPhone());
        profile.setContactEmail(dto.getContactEmail());
        // 4. 落库并同步到 AI 向量库（异步执行，失败不影响主流程）
        enterpriseProfileMapper.update(profile);
        profileSyncService.syncProfile(profile);
    }

    /** 交付域回写履约表现：仅更新 5 个履约字段（交付终态触发） */
    @Override
    public void updatePerformance(PerformanceUpdateDTO dto) {
        enterpriseProfileMapper.updatePerformance(dto.getTenantId(), dto.getOnTimeRate(),
                dto.getAcceptPassRate(), dto.getReturnCount(), dto.getPerfScore(), dto.getPerfSampleSize());
    }

    /**
     * 画像完整度评分：按身份类型动态计算，各自身份满分 100
     * <p>
     * 公共字段（36）：companyName 8、country 4、province 3、city 2、industry 5、description 4、contactName 5、contactPhone 3、contactEmail 2
     * 采购方字段（36）：buyCategory 18、buyVolume 10、buyMarket 8
     * 供应方字段（53）：supCategory 12、supCapacity 6、supCerts 12、supExport 3、mainProducts 8、keyClients 3、leadTime 3、employeeCount 3、annualRevenue 3
     * <p>
     * 纯采购方满分 = 36+36=72 → raw/72*100
     * 纯供应方满分 = 36+53=89 → raw/89*100
     * 双身份满分   = 36+36+53=125 → raw/125*100
     */
    private int computeScore(ApplicationInfoDTO app, DueDiligence dd, String contactName,
                             String contactPhone, String contactEmail, String profileType) {
        int raw = 0, max = 0;
        boolean isBuyer = "buyer".equals(profileType) || "both".equals(profileType);
        boolean isSupplier = "supplier".equals(profileType) || "both".equals(profileType);

        // ======== 公共字段（所有身份都考核） ========
        max += 8;  if (notBlank(app.getCompanyName())) raw += 8;
        max += 4;  if (notBlank(app.getCountry())) raw += 4;
        max += 3;  if (dd != null && notBlank(dd.getProvince())) raw += 3;
        max += 2;  if (dd != null && notBlank(dd.getCity())) raw += 2;
        max += 5;  if (dd != null && notBlank(dd.getIndustry())) raw += 5;
        max += 4;  if (dd != null && notBlank(dd.getDescription())) raw += 4;
        max += 5;  if (notBlank(contactName)) raw += 5;
        max += 3;  if (notBlank(contactPhone)) raw += 3;
        max += 2;  if (notBlank(contactEmail)) raw += 2;

        // ======== 采购方字段 ========
        if (isBuyer) {
            max += 18; if (notBlank(app.getBuyCategory())) raw += 18;
            max += 10; if (hasBuyAmount(dd, app) || notBlank(app.getBuyVolume())) raw += 10;
            max += 8;  if (notBlank(app.getBuyMarket())) raw += 8;
        }

        // ======== 供应方字段 ========
        if (isSupplier) {
            max += 12; if (notBlank(app.getSupCategory())) raw += 12;
            max += 6;  if (notBlank(app.getSupCapacity())) raw += 6;
            max += 12; if (notBlank(app.getSupCerts())) raw += 12;
            max += 3;  if (notBlank(app.getSupExport())) raw += 3;
            max += 8;  if (dd != null && notBlank(dd.getMainProducts())) raw += 8;
            max += 3;  if (dd != null && notBlank(dd.getKeyClients())) raw += 3;
            max += 3;  if (dd != null && notBlank(dd.getLeadTime())) raw += 3;
            max += 3;  if (dd != null && notBlank(dd.getEmployeeCount())) raw += 3;
            max += 3;  if (hasAnnualAmount(dd) || (dd != null && notBlank(dd.getAnnualRevenue()))) raw += 3;
        }

        return max > 0 ? raw * 100 / max : 0;
    }

    private void validateAmountFields(EditProfileDTO dto) {
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

    private boolean hasBuyAmount(DueDiligence dd, ApplicationInfoDTO app) {
        return (dd != null && CurrencyAmountValidator.isComplete(dd.getBuyVolumeAmount(), dd.getBuyVolumeCurrency()))
                || CurrencyAmountValidator.isComplete(app.getBuyVolumeAmount(), app.getBuyVolumeCurrency());
    }

    private boolean hasAnnualAmount(DueDiligence dd) {
        return dd != null && CurrencyAmountValidator.isComplete(dd.getAnnualRevenueAmount(), dd.getAnnualRevenueCurrency());
    }

    /** 判空：字符串非 null 且非空白 */
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
