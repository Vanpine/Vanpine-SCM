package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.ContractBatchPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属交易域，delivery 内为过渡副本（共享单库直查分批计划）；阶段 7/8 随桥接收敛。
 * 合同分批计划 Mapper
 * <p>
 * 仅保留 BaseMapper 内置能力（shipFromBatchPlan 按 ID 查单条用）；自定义查询方法已死代码清零
 * （发货排期/未发批次判定均已改走 trade 的 ContractClient Feign 契约）。
 *
 * @author gsc
 */
public interface ContractBatchPlanMapper extends BaseMapper<ContractBatchPlan> {
}
