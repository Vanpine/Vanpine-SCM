package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.trade.entity.Contract;
import cn.greensmartplanet.gsc.trade.entity.Inquiry;
import cn.greensmartplanet.gsc.trade.entity.InquiryAward;
import cn.greensmartplanet.gsc.trade.entity.Quote;
import cn.greensmartplanet.gsc.trade.mapper.ContractMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryAwardMapper;
import cn.greensmartplanet.gsc.trade.mapper.InquiryMapper;
import cn.greensmartplanet.gsc.trade.mapper.QuoteMapper;
import cn.greensmartplanet.gsc.trade.service.ContractPdfService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 合同 PDF 生成实现 — HTML 模板 + OpenHTMLtoPDF 渲染
 * <p>
 * 复用 fonts/NotoSansSC-Regular.ttf 中文字体 + OssService 上传链路（参考尽调报告）。
 * 模板文件：resources/templates/contract/standard.html（一期一份标准采购合同模板）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractPdfServiceImpl implements ContractPdfService {

    private static final String TEMPLATE_PATH = "templates/contract/standard.html";
    private static final String FONT_PATH = "fonts/NotoSansSC-Regular.ttf";
    private static final String FONT_FAMILY = "Noto Sans SC";

    private final ContractMapper contractMapper;
    private final InquiryMapper inquiryMapper;
    private final QuoteMapper quoteMapper;
    private final InquiryAwardMapper inquiryAwardMapper;
    // 画像归供应商域，Feign 查询（合同甲乙双方联系信息/地址）
    private final ProfileClient profileClient;
    // 共享企业名查询（批量，消 N+1）
    private final ProfileNameHelper profileNameHelper;
    private final OssService ossService;

    /**
     * 生成合同 PDF：组装数据 → HTML 模板渲染 → OpenHTMLtoPDF 转 PDF → 上传 OSS 返回下载地址
     */
    @Override
    public Map<String, String> generate(Long contractId) {
        Contract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw new BizException(ErrorCode.PROCUREMENT_CONTRACT_NOT_FOUND);
        }

        // 组装 PDF 数据
        Inquiry inquiry = contract.getInquiryId() != null ? inquiryMapper.selectById(contract.getInquiryId()) : null;
        Quote quote = contract.getWinningQuoteId() != null ? quoteMapper.selectById(contract.getWinningQuoteId()) : null;
        // MP 等价 SQL：SELECT ... FROM inquiry_award WHERE quote_id = #{quoteId};
        // 用于给 PDF 展示实际授标数量；业务上一个报价最多对应一条授标明细，所以用 selectOne
        InquiryAward award = quote != null ? inquiryAwardMapper.selectOne(
                new LambdaQueryWrapper<InquiryAward>()
                        .eq(InquiryAward::getQuoteId, quote.getId())) : null;
        // 批量查甲乙双方企业名（一次 Feign 批量查询，消 N+1；ProfileNameHelper 内部判空降级）
        Set<Long> companyNameTenantIds = new HashSet<>();
        companyNameTenantIds.add(contract.getBuyerTenantId());
        companyNameTenantIds.add(contract.getSupplierTenantId());
        Map<Long, String> companyNames = profileNameHelper.getCompanyNames(companyNameTenantIds);
        String buyerName = companyNames.get(contract.getBuyerTenantId());
        String supplierName = companyNames.get(contract.getSupplierTenantId());

        String html = render(loadTemplate(), contract, inquiry, quote, award, buyerName, supplierName);

        // HTML 模板 → PDF → 上传 OSS（openhtmltopdf 仅支持 File 注册字体，先落临时文件）
        File fontFile = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            fontFile = File.createTempFile("noto-sans-sc", ".ttf");
            try (InputStream fontIs = new ClassPathResource(FONT_PATH).getInputStream();
                 FileOutputStream fos = new FileOutputStream(fontFile)) {
                fontIs.transferTo(fos);
            }

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFont(fontFile, FONT_FAMILY);
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();

            String fileName = "合同_" + contract.getContractNo() + ".pdf";
            // upload() 返回 object key，这里现签短效 URL 给前端即时预览
            String key = ossService.upload(baos.toByteArray(), "contract", fileName);
            String url = ossService.generateGetUrl(key);

            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            result.put("key", key);
            result.put("name", fileName);
            log.info("合同 PDF 生成成功: id={}, contractNo={}", contractId, contract.getContractNo());
            return result;
        } catch (IOException e) {
            log.error("合同 PDF 生成失败: id={}", contractId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        } finally {
            if (fontFile != null && fontFile.exists()) {
                fontFile.delete();
            }
        }
    }

    /**
     * 读取模板文件内容
     */
    private String loadTemplate() {
        try (InputStream is = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    /**
     * 占位符替换渲染模板
     */
    private String render(String template, Contract c, Inquiry inquiry, Quote quote, InquiryAward award,
                          String buyerName, String supplierName) {
        Map<String, String> data = new HashMap<>();
        data.put("contractNo", nvl(c.getContractNo()));
        data.put("buyerName", nvl(buyerName));
        data.put("supplierName", nvl(supplierName));
        data.put("category", inquiry != null ? nvl(inquiry.getCategory()) : "");
        data.put("quantity", award != null && award.getAwardedQuantity() != null ? String.valueOf(award.getAwardedQuantity()) : "");
        data.put("unitPrice", quote != null && quote.getUnitPrice() != null ? quote.getUnitPrice().toPlainString() : "");
        data.put("amount", c.getAmount() != null ? c.getAmount().toPlainString() : "");
        data.put("currency", c.getCurrency() != null ? c.getCurrency() : "CNY");
        data.put("deliveryPlace", nvl(c.getDeliveryPlace()));
        data.put("deliveryDate", c.getDeliveryDate() != null ? c.getDeliveryDate().toString() : "");
        data.put("deliveryTerms", nvl(c.getDeliveryTerms()));
        data.put("paymentTerms", nvl(c.getPaymentTerms()));
        data.put("validUntil", c.getValidUntil() != null ? c.getValidUntil().toString() : "");

        // 甲乙双方联系人信息（画像归供应商域，一次 Feign 批量查询消 N+1；降级 null → 空串兜底）
        List<Long> profileTenantIds = new ArrayList<>();
        if (c.getBuyerTenantId() != null) profileTenantIds.add(c.getBuyerTenantId());
        if (c.getSupplierTenantId() != null) profileTenantIds.add(c.getSupplierTenantId());
        Map<Long, ProfileInfoDTO> profileMap = profileClient.getByTenantIds(profileTenantIds).getData();
        ProfileInfoDTO buyerProfile = profileMap != null ? profileMap.get(c.getBuyerTenantId()) : null;
        ProfileInfoDTO supplierProfile = profileMap != null ? profileMap.get(c.getSupplierTenantId()) : null;
        data.put("buyerContact", buyerProfile != null ? nvl(buyerProfile.getContactName()) : "");
        data.put("buyerPhone", buyerProfile != null ? nvl(buyerProfile.getContactPhone()) : "");
        data.put("buyerEmail", buyerProfile != null ? nvl(buyerProfile.getContactEmail()) : "");
        data.put("buyerLegalRep", buyerProfile != null ? nvl(buyerProfile.getLegalRep()) : "");
        data.put("buyerAddress", buyerProfile != null ? composeAddress(buyerProfile) : "");
        data.put("supplierContact", supplierProfile != null ? nvl(supplierProfile.getContactName()) : "");
        data.put("supplierPhone", supplierProfile != null ? nvl(supplierProfile.getContactPhone()) : "");
        data.put("supplierEmail", supplierProfile != null ? nvl(supplierProfile.getContactEmail()) : "");
        data.put("supplierLegalRep", supplierProfile != null ? nvl(supplierProfile.getLegalRep()) : "");
        data.put("supplierAddress", supplierProfile != null ? composeAddress(supplierProfile) : "");
        // 询盘规格与单位
        data.put("specText", inquiry != null ? nvl(inquiry.getSpecText()) : "");
        data.put("unit", inquiry != null ? nvl(inquiry.getUnit()) : "");

        String html = template;
        for (Map.Entry<String, String> e : data.entrySet()) {
            html = html.replace("{{" + e.getKey() + "}}", escapeHtml(e.getValue()));
        }
        return html;
    }

    /**
     * null → 空串
     */
    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * 拼接注册地址：优先用 registered_address，没有则从 country+province+city 组合
     */
    private String composeAddress(ProfileInfoDTO p) {
        if (p.getRegisteredAddress() != null && !p.getRegisteredAddress().isBlank()) {
            return p.getRegisteredAddress();
        }
        StringBuilder sb = new StringBuilder();
        if (p.getCountry() != null && !p.getCountry().isBlank()) sb.append(p.getCountry());
        if (p.getProvince() != null && !p.getProvince().isBlank()) sb.append(p.getProvince());
        if (p.getCity() != null && !p.getCity().isBlank()) sb.append(p.getCity());
        return sb.toString();
    }

    /**
     * 转义 HTML 特殊字符，防止合同内容破坏模板结构
     */
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
