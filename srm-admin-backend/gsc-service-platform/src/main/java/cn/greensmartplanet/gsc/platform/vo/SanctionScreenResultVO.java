package cn.greensmartplanet.gsc.platform.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 制裁筛查结果 VO — screen 接口返回
 *
 * @author gsc
 */
@Data
public class SanctionScreenResultVO {

    /** 关联入驻申请 */
    private Long applicationId;

    /** 筛查结果：clean / warning / pending（名单库为空） */
    private String status;

    /** 命中条数 */
    private Integer hitCount;

    /** 命中明细（warning 时非空） */
    private List<SanctionMatchVO> matches;

    /** 筛查时间 */
    private LocalDateTime screenedAt;
}
