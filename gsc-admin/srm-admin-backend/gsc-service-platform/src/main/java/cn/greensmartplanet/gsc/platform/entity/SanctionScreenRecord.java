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
 * 制裁筛查记录实体 — 入驻审核环节对申请做的制裁名单筛查结果留痕
 * <p>
 * 软提醒（黄牌）不阻断审核：status=warning 表示命中需人工复核，超管自行决定通过/驳回。
 * matches_json 存命中明细（名单名/程序/国别），供详情页展示。
 *
 * @author gsc
 */
@Data
@TableName("sanction_screen_record")
public class SanctionScreenRecord {

    /** 主键（雪花 ID，应用层 IdWorker 生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联入驻申请 */
    private Long applicationId;

    /** 租户 ID（审核通过后回填，筛查阶段为空） */
    private Long tenantId;

    /** 筛查输入：公司名 */
    private String queryName;

    /** 筛查输入：注册号 */
    private String queryRegNo;

    /** 命中条数 */
    private Integer hitCount;

    /** 筛查结果：clean / warning / pending（名单库为空） */
    private String status;

    /** 命中明细 JSON（名单名/程序/国别列表） */
    private String matchesJson;

    /** 筛查时间 */
    private LocalDateTime screenedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer isDeleted;
}
