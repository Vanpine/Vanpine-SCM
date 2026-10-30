package cn.greensmartplanet.gsc.supplier.service;

import cn.greensmartplanet.gsc.supplier.dto.CreateConnectionDTO;
import cn.greensmartplanet.gsc.supplier.vo.ConnectionVO;

/**
 * 对接生命周期服务接口 — 发起/申请/接受/拒绝/关闭（状态机）
 *
 * @author gsc
 */
public interface ConnectionLifecycleService {

    /** 采购方发起对接意向 */
    ConnectionVO create(Long buyerTenantId, Long userId, CreateConnectionDTO dto);

    /** 供应商主动申请需求（需求广场 → 发起对接） */
    ConnectionVO apply(Long requirementId, Long supplierTenantId, Long userId, String message);

    /** 接受对接（非发起方可操作） */
    void accept(Long id, Long tenantId, Long userId);

    /** 拒绝对接（非发起方可操作） */
    void reject(Long id, Long tenantId, Long userId, String replyMessage);

    /** 发起方关闭对接（pending → closed） */
    void close(Long id, Long tenantId, Long userId);
}
