package cn.greensmartplanet.gsc.trade.service;

import cn.greensmartplanet.gsc.trade.dto.CreateQuoteDTO;
import cn.greensmartplanet.gsc.trade.vo.QuoteVO;

/**
 * 报价生命周期服务接口 — 提交 / 撤回报价（写操作） quote 引用，出价，报价
 * <p>
 * 报价提交规则：校验询盘状态/截止/币种/受邀关系，已有 pending 报价原地更新，
 * 首次报价把询盘 open → quoting。
 *
 * @author gsc
 */
public interface QuoteLifecycleService {

    /**
     * 提交报价：校验询盘状态/截止时间/币种/受邀关系，已有 pending 报价原地更新，首次报价把询盘 open→quoting
     */
    QuoteVO submit(Long tenantId, Long userId, CreateQuoteDTO dto);

    /**
     * 撤回报价：中标或截止后不可撤回，成功后通知采购方
     */
    void withdraw(Long id, Long tenantId, Long userId);
}
