package cn.greensmartplanet.gsc.supplier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 编辑企业画像请求 DTO
 *
 * @author gsc
 */
@Data
@Schema(description = "编辑企业画像")
public class EditProfileDTO {

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "所在国家")
    private String country;

    @Schema(description = "省份")
    private String province;

    @Schema(description = "城市")
    private String city;

    @Schema(description = "采购品类")
    private String buyCategories;

    @Schema(description = "年采购额（1-5档）")
    private String buyVolume;
    @Schema(description = "年采购金额（原币种；推荐字符串传输）")
    private BigDecimal buyVolumeAmount;
    @Schema(description = "年采购币种（ISO 4217）")
    private String buyVolumeCurrency;
    @Schema(description = "财务期间开始日期")
    private LocalDate fiscalPeriodStart;
    @Schema(description = "财务期间结束日期")
    private LocalDate fiscalPeriodEnd;

    @Schema(description = "目标市场")
    private String buyMarket;

    @Schema(description = "生产品类")
    private String supCategories;

    @Schema(description = "年产能")
    private String supCapacity;

    @Schema(description = "资质认证")
    private String supCerts;

    @Schema(description = "出口经验（0-4档）")
    private String supExport;

    @Schema(description = "主要产品/服务")
    private String mainProducts;

    @Schema(description = "所属行业")
    private String industry;

    @Schema(description = "主要客户")
    private String keyClients;

    @Schema(description = "交货周期")
    private String leadTime;

    @Schema(description = "员工人数（1-5档）")
    private String employeeCount;

    @Schema(description = "年营业额（1-5档）")
    private String annualRevenue;
    @Schema(description = "年营收金额（原币种；推荐字符串传输）")
    private BigDecimal annualRevenueAmount;
    @Schema(description = "年营收币种（ISO 4217）")
    private String annualRevenueCurrency;

    @Schema(description = "企业简介")
    private String description;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;
}
