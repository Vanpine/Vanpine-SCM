package cn.greensmartplanet.gsc.platform.vo;

import lombok.Data;

/**
 * 制裁命中明细 VO — 筛查结果中的单条命中
 *
 * @author gsc
 */
@Data
public class SanctionMatchVO {

    /** 命中的名单主体名 */
    private String entryName;

    /** 名单程序（OFAC/UN/EU/…，分号分隔） */
    private String program;

    /** 国别/地区 */
    private String jurisdiction;

    /** 实体类型：Company/Organization/Person/Vessel/Aircraft */
    private String entityType;
}
