package cn.greensmartplanet.gsc.trade.service;

import java.util.Map;

/**
 * 合同 PDF 生成服务
 *
 * @author gsc
 */
public interface ContractPdfService {

    /**
     * 生成标准采购合同 PDF 并上传 OSS
     *
     * @param contractId 合同ID
     * @return {url, name} — OSS 预签名 URL 与文件名
     */
    Map<String, String> generate(Long contractId);
}
