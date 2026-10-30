package cn.greensmartplanet.gsc.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 制裁名单库实体 — OpenSanctions consolidated sanctions 本地落库
 * <p>
 * 数据源：{@code targets.simple.csv}（每日定时拉取），entity_id 为 OpenSanctions 实体 ID 作幂等键，
 * 重复拉取按 entity_id upsert 去重。name + aliases 多语种变体供筛查匹配（Phase 2）。
 *
 * @author gsc
 */
@Data
@TableName("sanction_entry")
public class SanctionEntry {

    /** 主键（雪花 ID，应用层 IdWorker 生成显式传入，与 delivery_promise_date 同款） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** OpenSanctions 实体 ID（幂等键，重拉去重） */
    private String entityId;

    /** 主体名称 */
    private String name;

    /** 别名（分号分隔，多语种变体，可能为空） */
    private String aliases;

    /** 实体类型：Company/Organization/Person/Vessel/Aircraft */
    private String entityType;

    /** 国别/地区（ISO 码，逗号分隔） */
    private String jurisdiction;

    /** 名单程序（program_ids，分号分隔） */
    private String program;

    /** 来源名单名（dataset，分号分隔） */
    private String sourceName;

    /** 最近同步时间 */
    private LocalDateTime lastSyncedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
