package cn.greensmartplanet.gsc.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 制裁名单同步状态 VO — 名单库页顶部展示 + 前端同步后轮询
 *
 * @author gsc
 */
@Data
public class SanctionSyncStatusVO {

    /** 库内名单总条数 */
    private Long totalCount;

    /** 最近一次成功同步时间（从未同步为 null） */
    private LocalDateTime lastSyncedAt;

    /** 最近一次同步状态：running / success / failed / never */
    private String lastSyncStatus;

    /** 最近一次同步失败信息（成功为 null） */
    private String lastSyncError;

    /** 本次同步 upsert 条数（成功时有效） */
    private Integer syncedCount;

    /** 本次同步的名单版本号 */
    private String version;
}
