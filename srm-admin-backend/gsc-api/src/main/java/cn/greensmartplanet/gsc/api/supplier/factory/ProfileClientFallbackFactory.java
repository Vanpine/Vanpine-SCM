package cn.greensmartplanet.gsc.api.supplier.factory;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.PerformanceUpdateDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 供应商域 Feign 降级工厂 — 画像查询降级返回 null/空
 * <p>
 * 决策依据（与平台域降级抛异常相反）：画像用于通知文案企业名、角色身份过滤等
 * 非数据底座场景，供应商服务不可用时降级放行（文案显示兜底名），
 * 不阻断主业务流程；配合告警日志人工兜底。
 * <p>
 * ⚠️ 例外：交易域发布需求校验画像身份依赖 profileType——该场景在调用方
 * 判空后走"无画像"分支（抛 MATCH_PROFILE_NOT_FOUND），语义与原直查一致。
 *
 * @author gsc
 */
@Slf4j
@Component
public class ProfileClientFallbackFactory implements FallbackFactory<ProfileClient> {

    @Override
    public ProfileClient create(Throwable cause) {
        return new ProfileClient() {
            @Override
            public R<ProfileInfoDTO> getByTenantId(Long tenantId) {
                log.warn("供应商服务不可用，画像查询降级返回 null: tenantId={}, cause={}",
                        tenantId, cause != null ? cause.getMessage() : "unknown");
                return R.ok(null);
            }

            @Override
            public R<Map<Long, ProfileInfoDTO>> getByTenantIds(List<Long> tenantIds) {
                log.warn("供应商服务不可用，批量画像查询降级返回空: size={}, cause={}",
                        tenantIds != null ? tenantIds.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<Void> updatePerformance(PerformanceUpdateDTO dto) {
                log.error("供应商服务不可用，履约表现回写降级跳过（画像履约分将滞后）: tenantId={}, cause={}",
                        dto != null ? dto.getTenantId() : null,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(null);
            }

            @Override
            public R<Void> initFromApplication(Long applicationId) {
                // 数据底座不可静默通过：画像初始化失败抛异常回滚尽调确认，与原单体事务内 upsert 行为一致
                log.error("供应商服务不可用，画像初始化降级失败（尽调确认将回滚）: applicationId={}, cause={}",
                        applicationId, cause != null ? cause.getMessage() : "unknown");
                throw new BizException(ErrorCode.INTERNAL_ERROR, "供应商服务不可用，请稍后重试");
            }
        };
    }
}
