package cn.greensmartplanet.gsc.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作台统计 Mapper — 平台域本域计数（租户）
 * <p>
 * ⚠️ 阶段 8.1：Dashboard BFF 拆分，本域计数 SQL 从旧单体 DashboardMapper
 * 按表归属原样迁入（countTenantByStatuses），保证切流后工作台数字基线不变。
 * 注：原单体"待审核入驻"统计的是 tenant 表 status='pending'（历史口径），原样保留。
 *
 * @author gsc
 */
@Mapper
public interface DashboardStatsMapper {

    /** 超管：按状态统计租户（active/pending/suspended 与待审核入驻均为本方法，口径与原单体一致） */
    long countTenantByStatuses(@Param("statuses") List<String> statuses);
}
