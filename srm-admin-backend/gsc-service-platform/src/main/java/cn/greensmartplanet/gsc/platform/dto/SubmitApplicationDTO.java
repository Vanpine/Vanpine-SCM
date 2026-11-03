package cn.greensmartplanet.gsc.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 提交入驻申请 DTO — 官网"申请加入"表单提交
 * 与前端 JoinUs.vue 的 formData 结构对应
 *
 * @author gsc
 */
@Data
@Schema(description = "提交入驻申请")
public class SubmitApplicationDTO {

    @NotBlank(message = "护照UUID不能为空")
    @Schema(description = "AI世界护照用户UUID", example = "uuid-xxx")
    private String passportUuid;

    @NotEmpty(message = "申请身份不能为空")
    @Schema(description = "申请身份 buyer / supplier", example = "[\"buyer\", \"supplier\"]")
    private List<String> role;

    @NotNull
    @Valid
    @Schema(description = "公司信息")
    private CompanyInfo company;

    @NotNull
    @Valid
    @Schema(description = "联系人信息")
    private ContactInfo contact;

    @Schema(description = "采购商资料（role含buyer时必填）")
    @Valid
    private BuyerProfile buyerProfile;

    @Schema(description = "供应商资料（role含supplier时必填）")
    @Valid
    private SupplierProfile supplierProfile;

    // ---- 嵌套对象 ----

    @Data
    @Schema(description = "公司信息")
    public static class CompanyInfo {
        @NotBlank(message = "公司名称不能为空")
        private String name;

        @NotBlank(message = "所在国家不能为空")
        private String country;

        @NotBlank(message = "企业注册号不能为空")
        private String regNumber;

        @NotNull(message = "成立年份不能为空")
        private Integer establishedYear;
    }

    @Data
    @Schema(description = "联系人信息")
    public static class ContactInfo {
        @NotBlank(message = "联系人姓名不能为空")
        private String name;

        private String title;

        @NotBlank(message = "联系邮箱不能为空")
        private String email;

        @NotBlank(message = "联系电话不能为空")
        private String phone;
    }

    @Data
    @Schema(description = "采购商资料")
    public static class BuyerProfile {
        @NotBlank(message = "采购品类不能为空")
        private String category;

        @NotBlank(message = "年采购额不能为空")
        private String annualVolume;

        private BigDecimal annualVolumeAmount;
        private String annualVolumeCurrency;
        @Schema(description = "财务期间开始日期")
        private LocalDate fiscalPeriodStart;
        @Schema(description = "财务期间结束日期")
        private LocalDate fiscalPeriodEnd;

        private String targetMarkets;
    }

    @Data
    @Schema(description = "供应商资料")
    public static class SupplierProfile {
        @NotBlank(message = "生产品类不能为空")
        private String category;

        @NotBlank(message = "年产能不能为空")
        private String annualCapacity;

        private String certifications;

        private String exportExperience;
    }
}
