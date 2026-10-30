package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.SanctionScreenRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 制裁筛查记录 Mapper — 筛查结果留痕
 *
 * @author gsc
 */
@Mapper
public interface SanctionScreenRecordMapper extends BaseMapper<SanctionScreenRecord> {

    /**
     * 按入驻申请查最近一条筛查记录（重查时覆盖展示）
     */
    SanctionScreenRecord selectLatestByApplicationId(@Param("applicationId") Long applicationId);

    /**
     * 批量按申请ID查最近一条（列表页黄牌标识用，避免 N+1）
     */
    List<SanctionScreenRecord> selectLatestByApplicationIds(@Param("applicationIds") List<Long> applicationIds);
}
