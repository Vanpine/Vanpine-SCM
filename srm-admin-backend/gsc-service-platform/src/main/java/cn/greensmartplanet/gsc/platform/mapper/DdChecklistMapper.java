package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.DdChecklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 尽调核查清单 Mapper
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域（阶段 7 gsc-service-supplier），platform 内为过渡副本，supplier 上线后随桥接收敛删除/改 Feign。</p>
 * @author gsc
 */
@Mapper
public interface DdChecklistMapper {

    /**
     * 按尽调ID查核查清单
     */
    List<DdChecklist> selectByDdId(@Param("ddId") Long ddId);

    /**
     * 批量按尽调ID查核查清单（避免 N+1 查询）
     */
    List<DdChecklist> selectByDdIds(@Param("ddIds") List<Long> ddIds);

    /**
     * 批量插入核查项
     */
    int insertBatch(@Param("list") List<DdChecklist> list);

    /**
     * 更新单条核查项
     */
    int update(DdChecklist item);

    /**
     * 按ID查单条核查项
     */
    DdChecklist selectById(@Param("id") Long id);

    /**
     * 按尽调ID删除所有核查项（重新分配事务所时先清旧数据）
     */
    int deleteByDdId(@Param("ddId") Long ddId);
}
