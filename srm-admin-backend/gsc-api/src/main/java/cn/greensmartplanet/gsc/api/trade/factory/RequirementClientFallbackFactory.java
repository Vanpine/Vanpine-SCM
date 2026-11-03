package cn.greensmartplanet.gsc.api.trade.factory;

import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementMatchFieldsDTO;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementRecallQueryDTO;
import cn.greensmartplanet.gsc.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 交易域需求查询 Feign 降级工厂
 * <p>
 * 降级决策：
 * <ul>
 *   <li>对接流程（getById/completeIfTargetReached）：数据底座性质校验，返回 null/false
 *       让调用方走统一"需求不存在/不达标"分支（与直查为 null 语义一致），不静默通过</li>
 *   <li>匹配展示（getMatchFields/getByIds）：返回 null/空，匹配页最多无结果，不阻断页面</li>
 * </ul>
 * 契约约定：提供方 HTTP 错误仅代表服务故障（查询不存在走 data=null），
 * 因此降级工厂只处理故障分支，无需解析错误码。
 *
 * @author gsc
 */
@Slf4j
@Component
public class RequirementClientFallbackFactory implements FallbackFactory<RequirementClient> {

    @Override
    public RequirementClient create(Throwable cause) {
        return new RequirementClient() {
            @Override
            public R<RequirementInfoDTO> getById(Long id) {
                log.error("交易服务不可用，需求查询降级返回 null: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                return R.ok(null);
            }

            @Override
            public R<RequirementMatchFieldsDTO> getMatchFields(Long id) {
                log.warn("交易服务不可用，匹配字段查询降级返回 null: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                return R.ok(null);
            }

            @Override
            public R<Map<Long, RequirementInfoDTO>> getByIds(List<Long> ids) {
                log.warn("交易服务不可用，批量需求查询降级返回空: size={}, cause={}",
                        ids != null ? ids.size() : 0,
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(Map.of());
            }

            @Override
            public R<Boolean> completeIfTargetReached(Long id, long acceptedCount, int targetSupplierCount) {
                log.error("交易服务不可用，需求达标翻 completed 降级拒绝: id={}, cause={}",
                        id, cause != null ? cause.getMessage() : "unknown");
                return R.ok(false);
            }

            @Override
            public R<List<RequirementMatchFieldsDTO>> recallOpenForMatch(RequirementRecallQueryDTO query) {
                log.warn("交易服务不可用，找需求召回降级返回空: cause={}",
                        cause != null ? cause.getMessage() : "unknown");
                return R.ok(List.of());
            }
        };
    }
}
