package cn.greensmartplanet.gsc.api.trade.factory;

import cn.greensmartplanet.gsc.api.trade.InquiryClient;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易域询盘查询 Feign 降级工厂 — 降级返回空列表
 * <p>
 * "已拒绝该询盘"标注是智能匹配页的展示性提示（前端禁选集合），
 * 交易域不可用时最多不显示标注，不阻断匹配主流程。
 *
 * @author gsc
 */
@Slf4j
@Component
public class InquiryClientFallbackFactory implements FallbackFactory<InquiryClient> {

    @Override
    public InquiryClient create(Throwable cause) {
        return (requirementId, buyerTenantId) -> {
            log.warn("交易服务不可用，拒绝供应商查询降级返回空: requirementId={}, cause={}",
                    requirementId, cause != null ? cause.getMessage() : "unknown");
            return R.ok(List.of());
        };
    }
}
