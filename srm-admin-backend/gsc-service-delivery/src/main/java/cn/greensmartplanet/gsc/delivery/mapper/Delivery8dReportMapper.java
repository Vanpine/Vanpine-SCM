package cn.greensmartplanet.gsc.delivery.mapper;

import cn.greensmartplanet.gsc.delivery.entity.Delivery8dReport;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 8D 整改报告 Mapper
 *
 * @author gsc
 */
public interface Delivery8dReportMapper extends BaseMapper<Delivery8dReport> {

    /** 插入 8D 报告（雪花 id 显式传入） */
    int insert(Delivery8dReport report);

    /** 按ID查单条（逻辑删除过滤） */
    Delivery8dReport selectById(@Param("id") Long id);

    /** 按风险查 8D 报告 */
    Delivery8dReport selectByRiskId(@Param("riskId") Long riskId);

    /** 供应商提交 D1-D8（pending → submitted，八步全必填由 DTO 校验保证） */
    int submit(@Param("id") Long id,
               @Param("d1Team") String d1Team,
               @Param("d2Desc") String d2Desc,
               @Param("d3Containment") String d3Containment,
               @Param("d4RootCause") String d4RootCause,
               @Param("d5Corrective") String d5Corrective,
               @Param("d6Verification") String d6Verification,
               @Param("d7Prevention") String d7Prevention,
               @Param("d8Summary") String d8Summary);

    /** 采购方审核通过（submitted → approved） */
    int approve(@Param("id") Long id, @Param("auditBy") Long auditBy);

    /** 采购方打回（submitted → rejected，记录打回原因） */
    int reject(@Param("id") Long id, @Param("auditBy") Long auditBy, @Param("rejectReason") String rejectReason);
}
