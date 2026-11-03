package cn.greensmartplanet.gsc.supplier.mapper;

import cn.greensmartplanet.gsc.supplier.entity.DdChecklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 尽调核查清单 Mapper
 * <p>
 * ⚠️ 微服务拆分清理：insertBatch/update/deleteByDdId/selectByDdIds/selectById 已移除——
 * 事务所工作流（清单生成/打勾/重分配清空）在 platform 域 DdFlowController 走独立副本，
 * 本域仅 DdReportPdfServiceImpl 生成报告时读取清单（selectByDdId）。
 *
 * @author gsc
 */
@Mapper
public interface DdChecklistMapper {

    /**
     * 按尽调ID查核查清单
     */
    List<DdChecklist> selectByDdId(@Param("ddId") Long ddId);
}
