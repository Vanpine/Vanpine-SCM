package cn.greensmartplanet.gsc.trade.service;

/**
 * 合同签署服务接口 — 上传盖章件 / 确认盖章件签署
 * <p>
 * 签署阶段状态机：pending_supplier(待供应商确认) → pending_confirm(待确认盖章件) → signed(已签署)。
 * sign() 为旧路径兼容别名，语义已改为确认盖章件签署。
 *
 * @author gsc
 */
public interface ContractSignService {

    /**
     * 上传盖章件（任一方，pending_supplier → pending_confirm）
     *
     * @param key 盖章件的 OSS object key（前端上传接口 /admin/upload 返回的 key 字段）
     */
    void uploadSign(Long id, Long tenantId, Long userId, String key);

    /**
     * 确认盖章件签署（对方，pending_confirm → signed，signDate=当天）
     */
    void confirmSign(Long id, Long tenantId, Long userId);

    /**
     * 确认盖章件签署（sign 语义改造，保留旧路径兼容）
     */
    void sign(Long id, Long tenantId, Long userId);
}
