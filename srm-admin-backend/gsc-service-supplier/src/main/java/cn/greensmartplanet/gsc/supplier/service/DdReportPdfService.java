package cn.greensmartplanet.gsc.supplier.service;

import java.util.Map;

/**
 * 尽调报告 PDF 生成服务
 * <p>
 * 按套餐类型（basic/standard/premium）和语言（zh/en）动态生成尽调报告 PDF，
 * 系统数据自动填入，事务所填写区留空，生成后上传 OSS 并返回下载 URL。
 *
 * @author gsc
 */
public interface DdReportPdfService {

    /**
     * 生成尽调报告 PDF → 上传 OSS → 返回 URL 和文件名
     *
     * @param applicationId 入驻申请ID
     * @param lang          语言：zh 中文 / en 英文
     * @return { url: "https://oss.../xxx.pdf", name: "尽职调查报告_XXX_20260722.pdf" }
     */
    Map<String, String> generate(Long applicationId, String lang);
}
