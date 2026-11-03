package cn.greensmartplanet.gsc.supplier.template;

import java.util.List;
import java.util.Map;

/**
 * 尽调报告 PDF 模板 — 中英文标签集中管理
 * <p>
 * 所有章节标题、字段标签、提示文字统一在此定义，按 lang 参数（zh/en）返回对应语言。
 * 后续可迁移到 properties 文件实现完整 i18n。
 *
 * @author gsc
 */
public class DdReportTemplate {

    /** 获取标签：zh 返回中文，否则返回英文 */
    public static String t(String key, String lang) {
        return "zh".equals(lang) ? ZH.getOrDefault(key, key) : EN.getOrDefault(key, ZH.getOrDefault(key, key));
    }

    /**
     * 根据 packageType 返回中文套餐名称
     */
    public static String packageName(String packageType, String lang) {
        return "zh".equals(lang) ? PACKAGE_ZH.getOrDefault(packageType, packageType)
                : PACKAGE_EN.getOrDefault(packageType, packageType);
    }

    /**
     * 根据 applicantRole 返回中文/英文角色名称
     */
    public static String roleName(String role, String lang) {
        if (role == null) return "-";
        if ("zh".equals(lang)) return ROLE_ZH.getOrDefault(role, role);
        return ROLE_EN.getOrDefault(role, role);
    }

    // ======================== 中文标签 ========================

    private static final Map<String, String> ZH = Map.ofEntries(
            // 封面
            Map.entry("title", "尽职调查报告"),
            Map.entry("company_label", "企业："),
            Map.entry("firm_label", "事务所："),
            Map.entry("package_label", "套餐类型："),
            Map.entry("date_label", "生成日期："),
            Map.entry("confidential", "机密文件 · 仅供内部使用"),
            // 章节
            Map.entry("section_1", "一、企业基本信息"),
            Map.entry("section_2", "二、股东及股权结构"),
            Map.entry("section_3", "三、经营情况"),
            Map.entry("section_4", "四、财务状况"),
            Map.entry("section_5", "五、法律诉讼"),
            Map.entry("section_6", "六、资质认证"),
            Map.entry("section_7", "七、采购/供应能力"),
            Map.entry("section_8", "八、核查清单汇总"),
            Map.entry("section_9", "九、核查结论与签章"),
            // 字段标签 — 基本信息
            Map.entry("company_name", "企业名称"),
            Map.entry("reg_number", "注册号"),
            Map.entry("country", "所在国家"),
            Map.entry("established_year", "成立年份"),
            Map.entry("province", "省份"),
            Map.entry("city", "城市"),
            Map.entry("legal_person_name", "法人代表"),
            Map.entry("legal_person_id", "法人身份证号"),
            Map.entry("website", "企业官网"),
            Map.entry("description", "企业简介"),
            Map.entry("applicant_role", "申请身份"),
            // 联系信息
            Map.entry("contact_name", "联系人"),
            Map.entry("contact_title", "职位"),
            Map.entry("contact_email", "联系邮箱"),
            Map.entry("contact_phone", "联系电话"),
            // 股权结构
            Map.entry("shareholder_info", "股权结构文件"),
            Map.entry("shareholder_check", "股权结构核查意见"),
            // 经营情况
            Map.entry("employee_count", "员工人数"),
            Map.entry("annual_revenue", "年营业额"),
            Map.entry("annual_revenue_period", "年营收报表期间"),
            Map.entry("business_scope", "经营范围"),
            Map.entry("operation_check", "经营情况核查结论"),
            // 采购方
            Map.entry("buy_category", "采购品类"),
            Map.entry("buy_volume", "年采购额"),
            Map.entry("buy_market", "目标市场"),
            Map.entry("industry", "所属行业"),
            Map.entry("procurement_detail", "采购明细"),
            Map.entry("supplier_requirement", "供应商要求"),
            Map.entry("target_region", "目标区域"),
            Map.entry("payment_terms", "付款方式"),
            // 供应方
            Map.entry("sup_category", "生产品类"),
            Map.entry("sup_capacity", "年产能"),
            Map.entry("sup_certs", "资质认证"),
            Map.entry("sup_export", "出口经验"),
            Map.entry("main_products", "主要产品"),
            Map.entry("key_clients", "主要客户"),
            Map.entry("export_markets", "出口市场"),
            Map.entry("management_cert", "管理体系认证"),
            Map.entry("product_cert", "产品认证"),
            Map.entry("delivery_terms", "交货方式"),
            Map.entry("lead_time", "交货周期"),
            // 财务
            Map.entry("financial_report_url", "审计报告文件"),
            Map.entry("bank_credit", "银行信用核查"),
            Map.entry("tax_record", "税务记录核查"),
            // 法律
            Map.entry("legal_record", "诉讼记录"),
            Map.entry("administrative_penalty", "行政处罚核查"),
            Map.entry("intl_compliance", "国际合规核查"),
            // 核查清单
            Map.entry("checklist_item", "核查项"),
            Map.entry("checklist_result", "是否通过"),
            Map.entry("checklist_remark", "核查备注"),
            // 结论
            Map.entry("conclusion", "综合评级"),
            Map.entry("pass", "通过"),
            Map.entry("conditional_pass", "有条件通过"),
            Map.entry("fail", "不通过"),
            Map.entry("firm_signature", "事务所签章"),
            Map.entry("review_date", "核查日期"),
            Map.entry("generated_by", "本报告由全球供应链 AI 赋能平台自动生成"),
            // 通用
            Map.entry("yes", "是"),
            Map.entry("no", "否"),
            Map.entry("none", "无"),
            Map.entry("empty_placeholder", "—"),
            Map.entry("blank_line", "_______________"),
            // 附件
            Map.entry("attachment_list", "附件材料"),
            Map.entry("business_license", "营业执照"),
            Map.entry("attachment_urls", "其他附件")
    );

    // ======================== 英文标签 ========================

    private static final Map<String, String> EN = Map.ofEntries(
            // Cover
            Map.entry("title", "Due Diligence Report"),
            Map.entry("company_label", "Company: "),
            Map.entry("firm_label", "Firm: "),
            Map.entry("package_label", "Package: "),
            Map.entry("date_label", "Date: "),
            Map.entry("confidential", "Confidential · For Internal Use Only"),
            // Sections
            Map.entry("section_1", "I. Company Basic Information"),
            Map.entry("section_2", "II. Shareholders & Equity Structure"),
            Map.entry("section_3", "III. Business Operations"),
            Map.entry("section_4", "IV. Financial Status"),
            Map.entry("section_5", "V. Legal Proceedings"),
            Map.entry("section_6", "VI. Certifications & Qualifications"),
            Map.entry("section_7", "VII. Procurement / Supply Capability"),
            Map.entry("section_8", "VIII. Verification Checklist"),
            Map.entry("section_9", "IX. Conclusion & Sign-off"),
            // Field labels — Basic Info
            Map.entry("company_name", "Company Name"),
            Map.entry("reg_number", "Registration No."),
            Map.entry("country", "Country"),
            Map.entry("established_year", "Year Established"),
            Map.entry("province", "Province"),
            Map.entry("city", "City"),
            Map.entry("legal_person_name", "Legal Representative"),
            Map.entry("legal_person_id", "ID Number"),
            Map.entry("website", "Website"),
            Map.entry("description", "Company Description"),
            Map.entry("applicant_role", "Applicant Role"),
            // Contact
            Map.entry("contact_name", "Contact Person"),
            Map.entry("contact_title", "Title"),
            Map.entry("contact_email", "Email"),
            Map.entry("contact_phone", "Phone"),
            // Shareholder
            Map.entry("shareholder_info", "Shareholder Structure Document"),
            Map.entry("shareholder_check", "Shareholder Verification Note"),
            // Operations
            Map.entry("employee_count", "Employee Count"),
            Map.entry("annual_revenue", "Annual Revenue"),
            Map.entry("annual_revenue_period", "Annual Revenue Period"),
            Map.entry("business_scope", "Business Scope"),
            Map.entry("operation_check", "Operations Verification Conclusion"),
            // Buyer
            Map.entry("buy_category", "Procurement Category"),
            Map.entry("buy_volume", "Annual Procurement Volume"),
            Map.entry("buy_market", "Target Market"),
            Map.entry("industry", "Industry"),
            Map.entry("procurement_detail", "Procurement Detail"),
            Map.entry("supplier_requirement", "Supplier Requirement"),
            Map.entry("target_region", "Target Region"),
            Map.entry("payment_terms", "Payment Terms"),
            // Supplier
            Map.entry("sup_category", "Product Category"),
            Map.entry("sup_capacity", "Annual Capacity"),
            Map.entry("sup_certs", "Certifications"),
            Map.entry("sup_export", "Export Experience"),
            Map.entry("main_products", "Main Products"),
            Map.entry("key_clients", "Key Clients"),
            Map.entry("export_markets", "Export Markets"),
            Map.entry("management_cert", "Management Certifications"),
            Map.entry("product_cert", "Product Certifications"),
            Map.entry("delivery_terms", "Delivery Terms"),
            Map.entry("lead_time", "Lead Time"),
            // Financial
            Map.entry("financial_report_url", "Audit Report File"),
            Map.entry("bank_credit", "Bank Credit Check"),
            Map.entry("tax_record", "Tax Record Check"),
            // Legal
            Map.entry("legal_record", "Litigation Record"),
            Map.entry("administrative_penalty", "Administrative Penalty Check"),
            Map.entry("intl_compliance", "International Compliance Check"),
            // Checklist
            Map.entry("checklist_item", "Item"),
            Map.entry("checklist_result", "Pass"),
            Map.entry("checklist_remark", "Remark"),
            // Conclusion
            Map.entry("conclusion", "Overall Rating"),
            Map.entry("pass", "Pass"),
            Map.entry("conditional_pass", "Conditional Pass"),
            Map.entry("fail", "Fail"),
            Map.entry("firm_signature", "Firm Signature"),
            Map.entry("review_date", "Review Date"),
            Map.entry("generated_by", "This report was automatically generated by GSC AI Platform"),
            // General
            Map.entry("yes", "Yes"),
            Map.entry("no", "No"),
            Map.entry("none", "None"),
            Map.entry("empty_placeholder", "—"),
            Map.entry("blank_line", "_______________"),
            // Attachments
            Map.entry("attachment_list", "Attachments"),
            Map.entry("business_license", "Business License"),
            Map.entry("attachment_urls", "Other Attachments")
    );

    // ======================== 套餐名称 ========================

    private static final Map<String, String> PACKAGE_ZH = Map.of(
            "basic", "基础套餐",
            "standard", "标准套餐",
            "premium", "高级套餐"
    );

    private static final Map<String, String> PACKAGE_EN = Map.of(
            "basic", "Basic",
            "standard", "Standard",
            "premium", "Premium"
    );

    /** 获取套餐服务清单（中文） */
    public static String packageServices(String packageType, String lang) {
        List<String> services = "zh".equals(lang)
                ? PACKAGE_SERVICES_ZH.getOrDefault(packageType, List.of())
                : PACKAGE_SERVICES_EN.getOrDefault(packageType, PACKAGE_SERVICES_ZH.getOrDefault(packageType, List.of()));
        if (services.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < services.size(); i++) {
            if (i > 0) sb.append("  ·  ");
            sb.append(services.get(i));
        }
        return sb.toString();
    }

    private static final Map<String, List<String>> PACKAGE_SERVICES_ZH = Map.of(
            "basic", List.of("工商注册核验", "法人背景核查", "诉讼记录核查", "财务报表审核", "7个工作日交付"),
            "standard", List.of("含基础套餐全部核查", "股权结构穿透", "董监高及银行征信核查", "经营模式与行业地位分析", "5个工作日交付"),
            "premium", List.of("含标准套餐全部核查", "财务税务及资金往来深度核查", "关联交易与公司治理审查", "尽调结论与上市可行性建议", "加急3个工作日·专属客户经理")
    );

    private static final Map<String, List<String>> PACKAGE_SERVICES_EN = Map.of(
            "basic", List.of("Business Registration Verification", "Legal Rep Background Check", "Litigation Record Check", "Financial Statement Review", "7 Business Days"),
            "standard", List.of("All Basic Checks Included", "Shareholder Structure Tracing", "Director & Bank Credit Check", "Business Model & Industry Analysis", "5 Business Days"),
            "premium", List.of("All Standard Checks Included", "In-depth Finance/Tax/Fund-flow Review", "Related-Party & Governance Review", "Conclusion & IPO Feasibility Advice", "Expedited 3 Days·Dedicated Manager")
    );

    // ======================== 角色名称 ========================

    private static final Map<String, String> ROLE_ZH = Map.of(
            "buyer", "采购商",
            "supplier", "供应商",
            "buyer,supplier", "采购商+供应商"
    );

    private static final Map<String, String> ROLE_EN = Map.of(
            "buyer", "Buyer",
            "supplier", "Supplier",
            "buyer,supplier", "Buyer & Supplier"
    );
}
