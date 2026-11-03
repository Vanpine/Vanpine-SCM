package cn.greensmartplanet.gsc.api.platform.factory;

import cn.greensmartplanet.gsc.api.platform.ApplicationClient;
import cn.greensmartplanet.gsc.api.platform.dto.ApplicationInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 平台域 Feign 降级工厂 — 平台服务不可用时抛 BizException，不静默通过
 * <p>
 * 决策依据（与交付域降级放行相反）：申请信息是尽调兜底/画像初始化/匹配回填的数据底座，
 * 缺了它产出的是"看起来正常但字段缺失"的业务结果；宁可报错暴露故障，
 * 也不让供应商域在数据不全时继续流转。
 *
 * @author gsc
 */
@Slf4j
@Component
public class ApplicationClientFallbackFactory implements FallbackFactory<ApplicationClient> {

    @Override
    public ApplicationClient create(Throwable cause) {
        return new ApplicationClient() {
            @Override
            public R<ApplicationInfoDTO> getById(Long id) {
                log.error("平台服务不可用，入驻申请查询降级失败: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "平台服务不可用，请稍后重试");
            }

            @Override
            public R<ApplicationInfoDTO> getByTenantId(Long tenantId) {
                log.error("平台服务不可用，入驻申请查询降级失败: tenantId={}, cause={}",
                        tenantId, cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "平台服务不可用，请稍后重试");
            }

            @Override
            public R<Map<Long, Long>> getIdsByTenantIds(List<Long> tenantIds) {
                log.error("平台服务不可用，批量申请映射查询降级失败: size={}, cause={}",
                        tenantIds != null ? tenantIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "平台服务不可用，请稍后重试");
            }
        };
    }
}
