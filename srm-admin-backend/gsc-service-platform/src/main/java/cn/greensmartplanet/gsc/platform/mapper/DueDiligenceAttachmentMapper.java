package cn.greensmartplanet.gsc.platform.mapper;

import cn.greensmartplanet.gsc.platform.entity.DueDiligenceAttachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 尽调附件材料 Mapper（多值附件）
 * <p>
 * 查询/新增/删除均可用 BaseMapper 通用方法（按 application_id 过滤时用 LambdaQueryWrapper）。
 * 无需手写 XML —— 本表无逻辑删除，条件简单。
 *
 * @author gsc
 */
@Mapper
public interface DueDiligenceAttachmentMapper extends BaseMapper<DueDiligenceAttachment> {
}
