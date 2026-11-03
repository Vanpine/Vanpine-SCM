package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.SanctionEntry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 制裁名单库 Mapper — 批量 upsert + 查询
 *
 * @author gsc
 */
@Mapper
public interface SanctionEntryMapper extends BaseMapper<SanctionEntry> {

    /**
     * 批量 upsert（INSERT ... ON DUPLICATE KEY UPDATE，按 entity_id 唯一键幂等）
     * <p>
     * 同步全量重建的核心：新记录插入，已有记录只更新非主键字段，不产生重复行。
     * 单次同步按 500 条一批分批调用，控制单条 SQL 体积。
     */
    int insertBatchUpsert(@Param("list") List<SanctionEntry> list);

    /**
     * 分页查询（超管名单库页）
     */
    List<SanctionEntry> selectPage(@Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("size") int size);

    /**
     * 分页总数
     */
    long count(@Param("keyword") String keyword);

    /**
     * 统计当前库内条数（同步状态展示）
     */
    long countAll();

    /**
     * 最近一次同步时间（同步状态展示）
     */
    LocalDateTime selectMaxSyncedAt();
}
