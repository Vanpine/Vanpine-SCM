package cn.greensmartplanet.gsc.trade.mapper;

import cn.greensmartplanet.gsc.trade.entity.ContractBatchPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同分批计划 Mapper
 *
 * @author gsc
 */
public interface ContractBatchPlanMapper extends BaseMapper<ContractBatchPlan> {

    /** 按合同查分批计划（batch_no 升序） */
    List<ContractBatchPlan> selectByContractId(@Param("contractId") Long contractId);

    /** 按合同ID批量查分批计划（列表回填用） */
    List<ContractBatchPlan> selectByContractIds(@Param("contractIds") List<Long> contractIds);

    /** 批量插入分批计划 */
    int batchInsert(@Param("list") List<ContractBatchPlan> list);

    /** 按合同逻辑删除分批计划（编辑合同重建计划用） */
    int deleteByContractId(@Param("contractId") Long contractId);
}
