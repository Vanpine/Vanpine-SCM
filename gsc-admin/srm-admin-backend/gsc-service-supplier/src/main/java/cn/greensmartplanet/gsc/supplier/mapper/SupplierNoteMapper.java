package cn.greensmartplanet.gsc.supplier.mapper;

import cn.greensmartplanet.gsc.supplier.entity.SupplierNote;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 供应商备注 Mapper
 * <p>
 * 备注归属租户隔离：插入由 Service 组装 ownerTenantId，列表查询按 ownerTenantId 过滤。
 *
 * @author gsc
 */
public interface SupplierNoteMapper extends BaseMapper<SupplierNote> {

    /** 插入备注（useGeneratedKeys 回填雪花主键） */
    int insert(SupplierNote note);
}
