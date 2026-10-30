package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.supplier.entity.DdChecklist;
import cn.greensmartplanet.gsc.supplier.entity.DueDiligence;
import cn.greensmartplanet.gsc.supplier.mapper.DdChecklistMapper;
import cn.greensmartplanet.gsc.supplier.mapper.DueDiligenceMapper;
import cn.greensmartplanet.gsc.supplier.service.DdReportPdfService;
import cn.greensmartplanet.gsc.supplier.template.DdReportTemplate;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 尽调报告 PDF 生成实现
 * <p>
 * 按套餐类型和语言动态生成尽调报告 PDF：封面 + 按套餐展开章节 + 核查清单表格 + 结论签章。
 * 生成后上传 OSS，返回下载 URL。
 * <p>
 * ⚠️ 微服务拆分：PDF 内容全部取自 due_diligence/dd_checklist（供应商域自表），
 * 不再依赖 application 表（原注入的 ApplicationMapper 仅查申请实体但 buildPdf 未使用其字段）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DdReportPdfServiceImpl implements DdReportPdfService {

    /**
     * A4 纸张尺寸
     */
    private static final Rectangle A4 = PageSize.A4;
    /**
     * 页边距
     */
    private static final float MARGIN = 50f;
    /**
     * 正文字体大小
     */
    private static final float FONT_SIZE = 10f;
    /**
     * 标题字体大小
     */
    private static final float TITLE_SIZE = 18f;
    /**
     * 章节标题字体大小
     */
    private static final float SECTION_SIZE = 14f;
    /** 中文字体（classpath，线上在 fat jar 内，不能当本地文件路径读） */
    private static final String FONT_RESOURCE = "fonts/NotoSansSC-Regular.ttf";
    private final DueDiligenceMapper dueDiligenceMapper;
    private final DdChecklistMapper ddChecklistMapper;
    private final OssService ossService;

    /**
     * 生成尽调报告 PDF → 上传 OSS → 返回 URL 和文件名
     *
     * @param applicationId 入驻申请ID
     * @param lang          语言（zh/en）
     * @return { url, name }
     */
    @Override
    public Map<String, String> generate(Long applicationId, String lang) {
        // 1. 查尽调记录（PDF 内容全部取自供应商域自表）
        DueDiligence dd = dueDiligenceMapper.selectByApplicationId(applicationId);
        if (dd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "尽调记录不存在");
        }

        try {
            // 2. 生成 PDF 字节流
            byte[] pdfBytes = buildPdf(dd, lang);

            // 3. 上传 OSS
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String companyName = dd.getCompanyName() != null ? dd.getCompanyName() : "企业";
            String fileName = (lang == null || "zh".equals(lang))
                    ? "尽职调查报告_" + companyName + "_" + dateStr + ".pdf"
                    : "DueDiligenceReport_" + companyName + "_" + dateStr + ".pdf";
            // upload() 返回 object key，这里现签短效 URL 给前端即时预览
            String key = ossService.upload(pdfBytes, "dd-report", fileName);
            String ossUrl = ossService.generateGetUrl(key);

            // 4. 只返回 URL+文件名给前端，不落库——报告状态和 URL 由事务所点"提交"
            //    （submitFirmResult）写入，避免一生成就影响超管侧报告状态
            Map<String, String> result = new LinkedHashMap<>();
            result.put("url", ossUrl);
            result.put("key", key);
            result.put("name", fileName);
            log.info("尽调报告 PDF 已生成, applicationId={}, lang={}, fileName={}", applicationId, lang, fileName);
            return result;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("尽调报告 PDF 生成失败, applicationId={}", applicationId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "PDF生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建 PDF 文档
     */
    private byte[] buildPdf(DueDiligence dd, String lang) throws Exception {
        File fontFile = copyFontToTempFile();
        try {
            return buildPdfWithFont(dd, lang, fontFile);
        } finally {
            if (!fontFile.delete()) {
                fontFile.deleteOnExit();
            }
        }
    }

    private byte[] buildPdfWithFont(DueDiligence dd, String lang, File fontFile) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        BaseFont zhFont = BaseFont.createFont(fontFile.getAbsolutePath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font titleFont = new Font(zhFont, TITLE_SIZE, Font.BOLD);
        Font sectionFont = new Font(zhFont, SECTION_SIZE, Font.BOLD);
        Font normalFont = new Font(zhFont, FONT_SIZE, Font.NORMAL);
        Font boldFont = new Font(zhFont, FONT_SIZE, Font.BOLD);
        Font smallFont = new Font(zhFont, 8f, Font.NORMAL);

        String packageType = dd.getPackageType() != null ? dd.getPackageType() : "basic";
        boolean isStandard = "standard".equals(packageType) || "premium".equals(packageType);
        boolean isPremium = "premium".equals(packageType);
        String role = dd.getApplicantRole() != null ? dd.getApplicantRole() : "";
        boolean isBuyer = role.contains("buyer");
        boolean isSupplier = role.contains("supplier");

        // ====== 封面 ======
        addCover(document, titleFont, normalFont, smallFont, dd, lang, packageType, zhFont);

        // ====== 一、企业基本信息 ======
        addSectionTitle(document, sectionFont, DdReportTemplate.t("section_1", lang));
        PdfPTable table1 = createTable(2, normalFont, boldFont);
        addRow(table1, "company_name", dd.getCompanyName(), lang, normalFont, boldFont);
        addRow(table1, "reg_number", dd.getRegNumber(), lang, normalFont, boldFont);
        addRow(table1, "country", dd.getCountry(), lang, normalFont, boldFont);
        addRow(table1, "established_year", dd.getEstablishedYear() != null ? String.valueOf(dd.getEstablishedYear()) : null, lang, normalFont, boldFont);
        addRow(table1, "province", dd.getProvince(), lang, normalFont, boldFont);
        addRow(table1, "city", dd.getCity(), lang, normalFont, boldFont);
        addRow(table1, "legal_person_name", dd.getLegalPersonName(), lang, normalFont, boldFont);
        addRow(table1, "legal_person_id", dd.getLegalPersonId(), lang, normalFont, boldFont);
        addRow(table1, "website", dd.getWebsite(), lang, normalFont, boldFont);
        addRow(table1, "description", dd.getDescription(), lang, normalFont, boldFont);
        addRow(table1, "applicant_role", DdReportTemplate.roleName(role, lang), lang, normalFont, boldFont);
        // 联系信息
        addSubTitle(table1, lang, normalFont, boldFont);
        addRow(table1, "contact_name", dd.getContactName(), lang, normalFont, boldFont);
        addRow(table1, "contact_title", dd.getContactTitle(), lang, normalFont, boldFont);
        addRow(table1, "contact_email", dd.getContactEmail(), lang, normalFont, boldFont);
        addRow(table1, "contact_phone", dd.getContactPhone(), lang, normalFont, boldFont);
        document.add(table1);

        // ====== 二、股东及股权结构（standard+premium） ======
        if (isStandard) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_2", lang));
            PdfPTable table2 = createTable(2, normalFont, boldFont);
            addFileLinkRow(table2, "shareholder_info", dd.getShareholderInfoKey(), lang, normalFont, boldFont);
            addBlankRow(table2, "shareholder_check", lang, normalFont, boldFont);
            document.add(table2);
        }

        // ====== 三、经营情况（standard+premium） ======
        if (isStandard) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_3", lang));
            PdfPTable table3 = createTable(2, normalFont, boldFont);
            // 年营收保留原始币种，不在 PDF 生成时做汇率换算
            addRow(table3, "employee_count", dd.getEmployeeCount(), lang, normalFont, boldFont);
            addRow(table3, "annual_revenue", formatAmount(dd.getAnnualRevenueAmount(), dd.getAnnualRevenueCurrency()), lang, normalFont, boldFont);
            addRow(table3, "annual_revenue_period", formatPeriod(dd.getFiscalPeriodStart(), dd.getFiscalPeriodEnd()), lang, normalFont, boldFont);
            // 经营情况核查结论（事务所填写）
            addBlankRow(table3, "operation_check", lang, normalFont, boldFont);
            document.add(table3);
        }

        // ====== 四、财务状况（仅 premium） ======
        if (isPremium) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_4", lang));
            PdfPTable table4 = createTable(2, normalFont, boldFont);
            addFileLinkRow(table4, "financial_report_url", dd.getFinancialReportKey(), lang, normalFont, boldFont);
            addBlankRow(table4, "bank_credit", lang, normalFont, boldFont);
            addBlankRow(table4, "tax_record", lang, normalFont, boldFont);
            document.add(table4);
        }

        // ====== 五、法律诉讼（仅 premium） ======
        if (isPremium) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_5", lang));
            PdfPTable table5 = createTable(2, normalFont, boldFont);
            // 诉讼文本与法律文书附件已分列存储，这里只展示文本
            String legalText = dd.getLegalRecordText();
            addRow(table5, "legal_record", legalText, lang, normalFont, boldFont);
            addBlankRow(table5, "administrative_penalty", lang, normalFont, boldFont);
            addBlankRow(table5, "intl_compliance", lang, normalFont, boldFont);
            document.add(table5);
        }

        // ====== 六、资质认证（standard+premium） ======
        if (isStandard) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_6", lang));
            PdfPTable table6 = createTable(2, normalFont, boldFont);
            addRow(table6, "management_cert", dd.getManagementCert(), lang, normalFont, boldFont);
            addRow(table6, "product_cert", dd.getProductCert(), lang, normalFont, boldFont);
            addRow(table6, "sup_certs", dd.getSupCerts(), lang, normalFont, boldFont);
            document.add(table6);
        }

        // ====== 七、采购/供应能力（standard+premium） ======
        if (isStandard) {
            addSectionTitle(document, sectionFont, DdReportTemplate.t("section_7", lang));
            PdfPTable table7 = createTable(2, normalFont, boldFont);
            if (isBuyer) {
                addRow(table7, "buy_category", dd.getBuyCategory(), lang, normalFont, boldFont);
                addRow(table7, "buy_volume", dd.getBuyVolume(), lang, normalFont, boldFont);
                addRow(table7, "buy_market", dd.getBuyMarket(), lang, normalFont, boldFont);
                addRow(table7, "industry", dd.getIndustry(), lang, normalFont, boldFont);
                addRow(table7, "procurement_detail", dd.getProcurementDetail(), lang, normalFont, boldFont);
                addRow(table7, "supplier_requirement", dd.getSupplierRequirement(), lang, normalFont, boldFont);
                addRow(table7, "target_region", dd.getTargetRegion(), lang, normalFont, boldFont);
                addRow(table7, "payment_terms", dd.getPaymentTerms(), lang, normalFont, boldFont);
            }
            if (isSupplier) {
                addRow(table7, "sup_category", dd.getSupCategory(), lang, normalFont, boldFont);
                addRow(table7, "sup_capacity", dd.getSupCapacity(), lang, normalFont, boldFont);
                addRow(table7, "sup_export", dd.getSupExport(), lang, normalFont, boldFont);
                addRow(table7, "main_products", dd.getMainProducts(), lang, normalFont, boldFont);
                addRow(table7, "key_clients", dd.getKeyClients(), lang, normalFont, boldFont);
                addRow(table7, "export_markets", dd.getExportMarkets(), lang, normalFont, boldFont);
                addRow(table7, "delivery_terms", dd.getDeliveryTerms(), lang, normalFont, boldFont);
                addRow(table7, "lead_time", dd.getLeadTime(), lang, normalFont, boldFont);
            }
            document.add(table7);
        }

        // ====== 八、核查清单汇总 ======
        addSectionTitle(document, sectionFont, DdReportTemplate.t("section_8", lang));
        if (dd.getId() != null) {
            List<DdChecklist> checklist = ddChecklistMapper.selectByDdId(dd.getId());
            if (checklist != null && !checklist.isEmpty()) {
                PdfPTable checklistTable = new PdfPTable(3);
                checklistTable.setWidthPercentage(100);
                checklistTable.setWidths(new float[]{4f, 1.5f, 3f});
                // 表头
                addChecklistHeader(checklistTable, lang, boldFont);
                // 数据行
                for (DdChecklist item : checklist) {
                    addChecklistRow(checklistTable, item, lang, normalFont);
                }
                document.add(checklistTable);
            } else {
                document.add(new Paragraph(DdReportTemplate.t("none", lang), normalFont));
            }
        }

        // ====== 九、核查结论与签章 ======
        addSectionTitle(document, sectionFont, DdReportTemplate.t("section_9", lang));
        PdfPTable table9 = createTable(2, normalFont, boldFont);
        // 综合评级：三个复选框
        Paragraph conclusionPara = new Paragraph();
        conclusionPara.add(new Chunk("□ " + DdReportTemplate.t("pass", lang) + "    ", normalFont));
        conclusionPara.add(new Chunk("□ " + DdReportTemplate.t("conditional_pass", lang) + "    ", normalFont));
        conclusionPara.add(new Chunk("□ " + DdReportTemplate.t("fail", lang), normalFont));
        PdfPCell conclusionCell = new PdfPCell(conclusionPara);
        conclusionCell.setColspan(2);
        conclusionCell.setPadding(6f);
        table9.addCell(labelCell(DdReportTemplate.t("conclusion", lang), boldFont));
        table9.addCell(valueCell("", normalFont));
        table9.addCell(conclusionCell);
        // 签章
        addBlankRow(table9, "firm_signature", lang, normalFont, boldFont);
        addBlankRow(table9, "review_date", lang, normalFont, boldFont);
        document.add(table9);

        // 页脚
        document.add(new Paragraph("\n\n"));
        Paragraph footer = new Paragraph(DdReportTemplate.t("generated_by", lang), smallFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        writer.close();
        return baos.toByteArray();
    }

    // ======================== PDF 构建辅助方法 ========================

    /**
     * 把 jar 内字体拷到临时文件。OpenPDF 只认真实文件路径，不能读 Spring Boot nested: URL。
     */
    private File copyFontToTempFile() throws Exception {
        ClassPathResource fontRes = new ClassPathResource(FONT_RESOURCE);
        File fontFile = File.createTempFile("noto-sans-sc-", ".ttf");
        try (InputStream in = fontRes.getInputStream();
             FileOutputStream out = new FileOutputStream(fontFile)) {
            in.transferTo(out);
        } catch (Exception e) {
            if (fontFile.exists() && !fontFile.delete()) {
                fontFile.deleteOnExit();
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "无法从 jar 读取中文字体: " + FONT_RESOURCE);
        }
        log.info("尽调报告字体已解出到临时文件: {}", fontFile.getAbsolutePath());
        return fontFile;
    }

    /**
     * 封面
     */
    private void addCover(Document doc, Font titleFont, Font normalFont, Font smallFont,
                          DueDiligence dd, String lang, String packageType,
                          BaseFont zhFont) throws DocumentException {
        // 空行撑开
        for (int i = 0; i < 6; i++) doc.add(new Paragraph(" "));

        Paragraph title = new Paragraph(DdReportTemplate.t("title", lang), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));

        // 企业名称
        Paragraph company = new Paragraph(
                DdReportTemplate.t("company_label", lang) + nvl(dd.getCompanyName()), normalFont);
        company.setAlignment(Element.ALIGN_CENTER);
        doc.add(company);
        // 事务所
        Paragraph firm = new Paragraph(
                DdReportTemplate.t("firm_label", lang) + nvl(dd.getDdFirmName()), normalFont);
        firm.setAlignment(Element.ALIGN_CENTER);
        doc.add(firm);
        // 套餐名称
        Paragraph pkg = new Paragraph(
                DdReportTemplate.t("package_label", lang)
                        + DdReportTemplate.packageName(packageType, lang), normalFont);
        pkg.setAlignment(Element.ALIGN_CENTER);
        doc.add(pkg);
        // 套餐服务清单
        String services = DdReportTemplate.packageServices(packageType, lang);
        if (!services.isEmpty()) {
            Paragraph svc = new Paragraph(services, new Font(zhFont, 9f, Font.NORMAL));
            svc.setAlignment(Element.ALIGN_CENTER);
            svc.setSpacingBefore(8f);
            doc.add(svc);
        }
        // 日期
        Paragraph date = new Paragraph(
                DdReportTemplate.t("date_label", lang)
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), normalFont);
        date.setAlignment(Element.ALIGN_CENTER);
        doc.add(date);

        doc.add(new Paragraph(" "));
        Paragraph conf = new Paragraph(DdReportTemplate.t("confidential", lang), smallFont);
        conf.setAlignment(Element.ALIGN_CENTER);
        doc.add(conf);

        doc.newPage();
    }

    /**
     * 章节标题
     */
    private void addSectionTitle(Document doc, Font font, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, font);
        p.setSpacingBefore(20f);
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    /**
     * 创建 2 列表格
     */
    private PdfPTable createTable(int columns, Font normalFont, Font boldFont) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.5f, 5.5f});
        table.setSpacingBefore(5f);
        table.setSpacingAfter(10f);
        return table;
    }

    /**
     * 添加普通数据行（标签 + 值）
     */
    private void addRow(PdfPTable table, String labelKey, String value,
                        String lang, Font normalFont, Font boldFont) {
        table.addCell(labelCell(DdReportTemplate.t(labelKey, lang), boldFont));
        table.addCell(valueCell(nvl(value), normalFont));
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null || currency == null || currency.isBlank()) {
            return null;
        }
        return currency + " " + amount.stripTrailingZeros().toPlainString();
    }

    private String formatPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        return startDate.format(DateTimeFormatter.ISO_LOCAL_DATE) + " ~ " + endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 添加文件链接行（标签 + 文件名链接文字）
     */
    private void addFileLinkRow(PdfPTable table, String labelKey, String urlValue,
                                String lang, Font normalFont, Font boldFont) {
        table.addCell(labelCell(DdReportTemplate.t(labelKey, lang), boldFont));
        // 附件字段现在存的是 OSS object key（如 dd/20260911/xxx/营业执照.pdf），取末段作展示名
        String display = "—";
        if (urlValue != null && !urlValue.isBlank()) {
            int idx = urlValue.lastIndexOf('/');
            display = idx > -1 ? urlValue.substring(idx + 1) : urlValue;
        }
        table.addCell(valueCell(display, normalFont));
    }

    /**
     * 添加事务所填写占位行
     */
    private void addBlankRow(PdfPTable table, String labelKey, String lang,
                             Font normalFont, Font boldFont) {
        table.addCell(labelCell(DdReportTemplate.t(labelKey, lang), boldFont));
        table.addCell(valueCell(DdReportTemplate.t("blank_line", lang), normalFont));
    }

    /**
     * 小标题 — 联系信息
     */
    private void addSubTitle(PdfPTable table, String lang, Font normalFont, Font boldFont) {
        PdfPCell cell = new PdfPCell(new Paragraph(" "));
        cell.setColspan(2);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2f);
        table.addCell(cell);
    }

    /**
     * 核查清单表头
     */
    private void addChecklistHeader(PdfPTable table, String lang, Font boldFont) {
        table.addCell(labelCell(DdReportTemplate.t("checklist_item", lang), boldFont));
        table.addCell(labelCell(DdReportTemplate.t("checklist_result", lang), boldFont));
        table.addCell(labelCell(DdReportTemplate.t("checklist_remark", lang), boldFont));
    }

    /**
     * 核查清单数据行
     */
    private void addChecklistRow(PdfPTable table, DdChecklist item, String lang, Font normalFont) {
        table.addCell(valueCell(nvl(item.getItemName()), normalFont));
        String passText = Boolean.TRUE.equals(item.getChecked())
                ? DdReportTemplate.t("yes", lang) : DdReportTemplate.t("no", lang);
        table.addCell(valueCell(passText, normalFont));
        table.addCell(valueCell(nvl(item.getRemark()), normalFont));
    }

    /**
     * 标签单元格（左列，灰色背景）
     */
    private PdfPCell labelCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(6f);
        cell.setGrayFill(0.95f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * 值单元格（右列）
     */
    private PdfPCell valueCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(6f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * null → "—"
     */
    private String nvl(String val) {
        return val == null || val.isBlank() ? "—" : val;
    }
}
