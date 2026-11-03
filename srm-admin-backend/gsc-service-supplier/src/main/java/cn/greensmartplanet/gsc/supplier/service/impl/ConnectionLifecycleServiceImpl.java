package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ConnectionStatus;
import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.supplier.dto.CreateConnectionDTO;
import cn.greensmartplanet.gsc.supplier.entity.Connection;
import cn.greensmartplanet.gsc.supplier.entity.Notification;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.service.ConnectionLifecycleService;
import cn.greensmartplanet.gsc.supplier.service.NotificationService;
import cn.greensmartplanet.gsc.supplier.vo.ConnectionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对接生命周期服务实现 — 对接状态机（发起/申请/接受/拒绝/关闭）
 * <p>
 * ⚠️ 阶段 9：需求表归交易域，需求存在/状态校验与达标翻 completed 全部走
 * RequirementClient Feign（降级 404/false 与直查为 null 的语义一致）；
 * 画像归本域，通知文案企业名走本域 ProfileClient 契约查询。
 * <p>
 * create/apply 共用私有 {@link #initiate} 状态机（查重→重开/新建→拼 VO→通知），
 * 消除约 60% 重复；通知构建统一走 {@link #notifyConnection}。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionLifecycleServiceImpl implements ConnectionLifecycleService {

    private final ConnectionMapper connectionMapper;
    // 需求表归交易域，Feign 查询（存在/状态校验、标题回填、达标回调）
    private final RequirementClient requirementClient;
    // 画像归本域，但经契约查询保证与外部调用方同一数据视图
    private final ProfileClient profileClient;
    private final NotificationService notificationService;

    /** 采购方发起对接：校验需求 open/供应商画像/不向自己发起，拒绝后可重新发起，成功后通知供应商 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionVO create(Long buyerTenantId, Long userId, CreateConnectionDTO dto) {
        // 1. 校验需求存在且为 open（Feign 查询契约 DTO，降级 null 语义与直查为 null 一致）
        RequirementInfoDTO req = requirementClient.getById(dto.getRequirementId()).getData();
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        if (!RequirementStatus.OPEN.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_CLOSED);
        }
        // 2. 校验供应商画像存在
        ProfileInfoDTO supplier = profileClient.getByTenantId(dto.getSupplierTenantId()).getData();
        if (supplier == null) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }
        // 3. 校验不向自己发起对接
        if (buyerTenantId.equals(dto.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 4. 通知文案企业名：采购方（通知接收方是供应商）
        String buyerName = getCompanyNameOrDefault(buyerTenantId, "采购方");
        String newContent = "【" + buyerName + "】向您发起了对接申请，需求：" + req.getTitle();
        String reapplyContent = "【" + buyerName + "】向您重新发起了对接申请，需求：" + req.getTitle();
        // 5. 核心状态机：查重 → 重开/新建 → 拼 VO → 通知供应商
        return initiate(req, supplier, buyerTenantId, dto.getSupplierTenantId(), userId, dto.getMessage(),
                "CONNECTION_CREATE", "新的对接申请", newContent, reapplyContent,
                dto.getSupplierTenantId(), buyerTenantId, ErrorCode.MATCH_ALREADY_CONNECTED);
    }

    /** 供应商主动申请对接（需求广场入口，支持拒绝后重新申请） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionVO apply(Long requirementId, Long supplierTenantId, Long userId, String message) {
        // 1. 校验需求存在且为 open（Feign 查询契约 DTO）
        RequirementInfoDTO req = requirementClient.getById(requirementId).getData();
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        if (!RequirementStatus.OPEN.getCode().equals(req.getStatus())) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_CLOSED);
        }
        // 2. 不向自己申请
        if (req.getTenantId() != null && req.getTenantId().equals(supplierTenantId)) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 3. 校验供应商身份（纯 buyer 不可申请需求）
        ProfileInfoDTO supplier = profileClient.getByTenantId(supplierTenantId).getData();
        if (supplier == null || (!"supplier".equals(supplier.getProfileType()) && !"both".equals(supplier.getProfileType()))) {
            throw new BizException(ErrorCode.MATCH_PROFILE_NOT_FOUND);
        }
        // 4. 通知文案企业名：供应商（通知接收方是需求方）
        String newContent = "【" + supplier.getCompanyName() + "】申请了您的需求《" + req.getTitle() + "》";
        String reapplyContent = "【" + supplier.getCompanyName() + "】重新申请了您的需求《" + req.getTitle() + "》";
        // 5. 核心状态机：查重 → 重开/新建 → 拼 VO → 通知需求方
        return initiate(req, supplier, req.getTenantId(), supplierTenantId, userId, message,
                "CONNECTION_APPLY", "新的对接申请", newContent, reapplyContent,
                req.getTenantId(), supplierTenantId, ErrorCode.MATCH_ALREADY_APPLIED);
    }

    /** 接受对接：非发起方操作，接受后回调交易域检查需求目标供应商数达标自动置 completed，并通知发起方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, Long tenantId, Long userId) {
        // 1. 查连接 + 校验 pending + 当前租户是买方或卖方
        Connection conn = getAndValidatePending(id, tenantId);
        // 2. 发起方不能接受自己的请求
        if (userId.equals(conn.getCreateBy())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 3. 更新状态
        int rows = connectionMapper.updateStatus(id, tenantId, ConnectionStatus.ACCEPTED.getCode(), null, userId);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        log.info("对接已接受, id={}, tenantId={}", id, tenantId);

        // 4. 需求归交易域：Feign 回调达标检查（交易域自行查目标数并翻 completed，幂等）
        long acceptedCount = connectionMapper.countAcceptedByRequirement(conn.getRequirementId());
        RequirementInfoDTO req = requirementClient.getById(conn.getRequirementId()).getData();
        if (req != null && RequirementStatus.OPEN.getCode().equals(req.getStatus())
                && req.getTargetSupplierCount() != null && req.getTargetSupplierCount() > 0) {
            Boolean reached = requirementClient.completeIfTargetReached(
                    conn.getRequirementId(), acceptedCount, req.getTargetSupplierCount()).getData();
            if (Boolean.TRUE.equals(reached)) {
                log.info("需求目标供应商数已达标, requirementId={}, target={}, accepted={}",
                        conn.getRequirementId(), req.getTargetSupplierCount(), acceptedCount);
            }
        }
        // 5. 站内消息：通知发起方对接已被接受
        Long receiverTenantId = tenantId.equals(conn.getBuyerTenantId()) ? conn.getSupplierTenantId() : conn.getBuyerTenantId();
        notifyConnection("CONNECTION_ACCEPT", "对接已接受",
                "【" + getCompanyNameOrDefault(tenantId, "对方") + "】接受了您的对接申请",
                receiverTenantId, conn.getId(), tenantId, userId);
    }

    /** 拒绝对接：非发起方操作，附拒绝原因并通知发起方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long tenantId, Long userId, String replyMessage) {
        // 1. 查连接 + 校验 pending + 当前租户是买方或卖方
        Connection conn = getAndValidatePending(id, tenantId);
        // 2. 发起方不能拒绝自己的请求
        if (userId.equals(conn.getCreateBy())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 3. 更新状态
        int rows = connectionMapper.updateStatus(id, tenantId, ConnectionStatus.REJECTED.getCode(), replyMessage, userId);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        log.info("对接已拒绝, id={}, tenantId={}", id, tenantId);

        // 4. 站内消息：通知发起方对接被拒绝
        Long receiverTenantId = tenantId.equals(conn.getBuyerTenantId()) ? conn.getSupplierTenantId() : conn.getBuyerTenantId();
        notifyConnection("CONNECTION_REJECT", "对接已拒绝",
                "【" + getCompanyNameOrDefault(tenantId, "对方") + "】拒绝了您的对接申请"
                        + (replyMessage != null && !replyMessage.isBlank() ? "：" + replyMessage : ""),
                receiverTenantId, conn.getId(), tenantId, userId);
    }

    /** 取消对接：仅发起方可操作，并通知对方 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, Long tenantId, Long userId) {
        // 1. 查连接 + 校验 pending + 当前租户是买方或卖方
        Connection conn = getAndValidatePending(id, tenantId);
        // 2. 只能由发起方关闭
        if (!userId.equals(conn.getCreateBy())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        // 3. 更新状态
        int rows = connectionMapper.updateStatus(id, tenantId, ConnectionStatus.CLOSED.getCode(), null, userId);
        if (rows == 0) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        log.info("对接已取消, id={}, tenantId={}", id, tenantId);

        // 4. 站内消息：通知对方对接已取消
        Long receiverTenantId = tenantId.equals(conn.getBuyerTenantId()) ? conn.getSupplierTenantId() : conn.getBuyerTenantId();
        notifyConnection("CONNECTION_CLOSE", "对接已取消",
                "【" + getCompanyNameOrDefault(tenantId, "对方") + "】取消了对接",
                receiverTenantId, conn.getId(), tenantId, userId);
    }

    // ==================== 私有：状态机与通知 ====================

    /**
     * 发起对接核心状态机（create/apply 共用）：查重 → 拒绝/取消后可重开，否则新建 → 拼 VO → 通知。
     *
     * @param req                已校验 open 的需求契约
     * @param supplier           已校验存在的供应商画像
     * @param buyerTenantId      需求方租户
     * @param supplierTenantId   供应方租户
     * @param message            申请留言
     * @param notifType          通知类型（CONNECTION_CREATE / CONNECTION_APPLY）
     * @param notifTitle         通知标题
     * @param notifNewContent    新建时通知内容
     * @param notifReapplyContent 重开时通知内容
     * @param notifReceiverTenantId 通知接收方租户
     * @param notifFromTenantId  通知发起方租户（create=采购方，apply=供应商）
     * @param alreadyCode        已存在 pending/accepted 时抛出的错误码
     */
    private ConnectionVO initiate(RequirementInfoDTO req, ProfileInfoDTO supplier,
                                  Long buyerTenantId, Long supplierTenantId, Long userId, String message,
                                  String notifType, String notifTitle,
                                  String notifNewContent, String notifReapplyContent,
                                  Long notifReceiverTenantId, Long notifFromTenantId,
                                  ErrorCode alreadyCode) {
        // 1. 防重复：同一需求+同一供应商已有记录
        Connection existing = connectionMapper.selectByRequirementAndTenant(req.getId(), supplierTenantId);
        if (existing != null) {
            // 1a. 拒绝/取消后可重新发起：重置 status=pending, 更新留言, 清空拒绝原因
            if (ConnectionStatus.REJECTED.getCode().equals(existing.getStatus())
                    || ConnectionStatus.CLOSED.getCode().equals(existing.getStatus())) {
                existing.setStatus(ConnectionStatus.PENDING.getCode());
                existing.setMessage(message);
                existing.setReplyMessage(null);
                existing.setCreateBy(userId);
                existing.setUpdateBy(userId);
                connectionMapper.updateForReapply(existing);
                log.info("重新发起对接, id={}, buyer={}, supplier={}", existing.getId(), buyerTenantId, supplierTenantId);
                ConnectionVO vo = ConnectionVO.fromEntity(existing);
                vo.setRequirementTitle(req.getTitle());
                vo.setSupplierCompanyName(supplier.getCompanyName());
                notifyConnection(notifType, notifTitle, notifReapplyContent, notifReceiverTenantId,
                        existing.getId(), notifFromTenantId, userId);
                return vo;
            }
            // 1b. pending/accepted → 不可重复
            throw new BizException(alreadyCode);
        }
        // 2. 新建对接
        Connection conn = Connection.builder()
                .requirementId(req.getId())
                .buyerTenantId(buyerTenantId)
                .supplierTenantId(supplierTenantId)
                .status(ConnectionStatus.PENDING.getCode())
                .message(message)
                .createBy(userId)
                .build();
        connectionMapper.insert(conn);
        log.info("对接意向已发起, id={}, buyer={}, supplier={}", conn.getId(), buyerTenantId, supplierTenantId);

        // 3. 拼 VO（挂需求标题 + 企业名）
        ConnectionVO vo = ConnectionVO.fromEntity(conn);
        vo.setRequirementTitle(req.getTitle());
        vo.setSupplierCompanyName(supplier.getCompanyName());
        // 4. 站内消息：通知接收方有新对接申请
        notifyConnection(notifType, notifTitle, notifNewContent, notifReceiverTenantId,
                conn.getId(), notifFromTenantId, userId);
        return vo;
    }

    /** 查连接 + 校验 pending + 当前租户是买方或卖方（accept/reject/close 共用） */
    private Connection getAndValidatePending(Long id, Long tenantId) {
        Connection conn = connectionMapper.selectById(id);
        if (conn == null || !ConnectionStatus.PENDING.getCode().equals(conn.getStatus())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        if (!tenantId.equals(conn.getBuyerTenantId()) && !tenantId.equals(conn.getSupplierTenantId())) {
            throw new BizException(ErrorCode.MATCH_INVALID_STATE);
        }
        return conn;
    }

    /** 站内消息统一构建（连接业务通知，5 处共用） */
    private void notifyConnection(String type, String title, String content, Long receiverTenantId,
                                  Long relatedId, Long fromTenantId, Long fromUserId) {
        notificationService.notify(Notification.builder()
                .type(type)
                .title(title)
                .content(content)
                .receiverTenantId(receiverTenantId)
                .relatedType("connection")
                .relatedId(relatedId)
                .fromTenantId(fromTenantId)
                .fromUserId(fromUserId)
                .build());
    }

    /** 企业名查询（通知文案用），降级显示兜底名 */
    private String getCompanyNameOrDefault(Long tenantId, String fallback) {
        ProfileInfoDTO profile = profileClient.getByTenantId(tenantId).getData();
        return profile != null ? profile.getCompanyName() : fallback;
    }
}
