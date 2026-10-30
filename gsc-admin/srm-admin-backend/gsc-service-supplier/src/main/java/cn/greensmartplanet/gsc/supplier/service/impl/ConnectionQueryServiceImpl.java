package cn.greensmartplanet.gsc.supplier.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ProfileClient;
import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.api.supplier.dto.ProfileInfoDTO;
import cn.greensmartplanet.gsc.api.trade.RequirementClient;
import cn.greensmartplanet.gsc.api.trade.dto.RequirementInfoDTO;
import cn.greensmartplanet.gsc.common.enums.ConnectionStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.supplier.entity.Connection;
import cn.greensmartplanet.gsc.supplier.mapper.ConnectionMapper;
import cn.greensmartplanet.gsc.supplier.projection.ConnectionCountRow;
import cn.greensmartplanet.gsc.supplier.service.ConnectionQueryService;
import cn.greensmartplanet.gsc.supplier.vo.ConnectionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对接记录查询服务实现 — 列表 / 我的对接详情 / 内部契约
 * <p>
 * 只读查询。VO 拼装采用「收集 ID → 一次 Feign 批量查 → Map 组装」消 N+1（红线 1）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionQueryServiceImpl implements ConnectionQueryService {

    private final ConnectionMapper connectionMapper;
    // 需求表归交易域，Feign 查询（标题回填）
    private final RequirementClient requirementClient;
    // 画像归本域，但经契约查询保证与外部调用方同一数据视图
    private final ProfileClient profileClient;

    /** 分页查对接记录（采购/供应双视角，按 role 区分），附企业名/需求标题/联系方式 */
    @Override
    public Map<String, Object> listMyConnections(Long tenantId, int pageNum, int pageSize, String status,
                                                 String keyword, String role, Long requirementId) {
        int offset = (pageNum - 1) * pageSize;
        List<Connection> list = connectionMapper.selectMyConnections(tenantId, status, keyword, role, requirementId, offset, pageSize);
        long total = connectionMapper.countMyConnections(tenantId, status, keyword, role, requirementId);

        List<ConnectionVO> records = enrichVOs(list);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /** 查某需求下当前租户的对接记录（联系方式仅 accepted 可见） */
    @Override
    public ConnectionVO getMyConnectionByRequirement(Long requirementId, Long tenantId) {
        // 1. 查对接记录
        Connection conn = connectionMapper.selectByRequirementAndTenant(requirementId, tenantId);
        if (conn == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 2. 拼 VO（联系方式仅 accepted 可见，脱敏逻辑已内置在 enrichVO 中）
        return enrichVO(conn);
    }

    // ==================== 内部契约（交易域 Feign 调用） ====================

    /** 按需求ID批量查已接受对接数（requirementId → count） */
    @Override
    public Map<Long, Long> countAcceptedByRequirementIds(Long buyerTenantId, List<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>();
        for (ConnectionCountRow row : connectionMapper.countGroupByRequirementIds(buyerTenantId, requirementIds)) {
            map.put(row.getRequirementId(), row.getCnt());
        }
        return map;
    }

    /** 查某租户在多个需求下的对接状态（requirementId → DTO；无记录不在 map 中） */
    @Override
    public Map<Long, MyConnectionStatusDTO> getMyConnectionStatusByRequirementIds(Long tenantId, List<Long> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, MyConnectionStatusDTO> map = new HashMap<>();
        connectionMapper.batchSelectByRequirementIds(tenantId, requirementIds)
                .forEach(c -> map.put(c.getRequirementId(), MyConnectionStatusDTO.builder()
                        .status(c.getStatus())
                        .replyMessage(c.getReplyMessage())
                        .build()));
        return map;
    }

    /** 批量拼 VO：需求标题与双方企业名批量 Feign 查询，避免逐条调用放大 */
    private List<ConnectionVO> enrichVOs(List<Connection> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        // 1. 批量查需求标题（需求归交易域，Feign 批量）
        Set<Long> reqIds = new HashSet<>();
        list.forEach(c -> {
            if (c.getRequirementId() != null) reqIds.add(c.getRequirementId());
        });
        Map<Long, String> titleMap = reqIds.isEmpty() ? Map.of()
                : requirementClient.getByIds(new ArrayList<>(reqIds)).getData().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().getTitle()));
        // 2. 批量查双方企业画像（画像归本域，契约批量）
        Set<Long> tenantIds = new HashSet<>();
        list.forEach(c -> {
            if (c.getBuyerTenantId() != null) tenantIds.add(c.getBuyerTenantId());
            if (c.getSupplierTenantId() != null) tenantIds.add(c.getSupplierTenantId());
        });
        Map<Long, ProfileInfoDTO> profileMap = tenantIds.isEmpty() ? Map.of()
                : profileClient.getByTenantIds(new ArrayList<>(tenantIds)).getData();

        return list.stream().map(c -> enrichVO(c, titleMap, profileMap)).toList();
    }

    /** 单条拼 VO（标题/画像单查） */
    private ConnectionVO enrichVO(Connection c) {
        Map<Long, String> titleMap = Map.of();
        Map<Long, ProfileInfoDTO> profileMap = Map.of();
        if (c.getRequirementId() != null) {
            RequirementInfoDTO req = requirementClient.getById(c.getRequirementId()).getData();
            if (req != null) {
                titleMap = Map.of(c.getRequirementId(), req.getTitle());
            }
        }
        Set<Long> ids = new HashSet<>();
        if (c.getBuyerTenantId() != null) ids.add(c.getBuyerTenantId());
        if (c.getSupplierTenantId() != null) ids.add(c.getSupplierTenantId());
        if (!ids.isEmpty()) {
            profileMap = profileClient.getByTenantIds(new ArrayList<>(ids)).getData();
        }
        return enrichVO(c, titleMap, profileMap);
    }

    /** VO 挂企业名、需求标题和对方联系方式（联系方式仅 accepted 状态返回） */
    private ConnectionVO enrichVO(Connection c, Map<Long, String> titleMap, Map<Long, ProfileInfoDTO> profileMap) {
        ConnectionVO vo = ConnectionVO.fromEntity(c);
        // 需求标题
        if (c.getRequirementId() != null) {
            vo.setRequirementTitle(titleMap.get(c.getRequirementId()));
        }
        // 采购方企业名
        ProfileInfoDTO buyer = profileMap.get(c.getBuyerTenantId());
        if (buyer != null) {
            vo.setBuyerCompanyName(buyer.getCompanyName());
        }
        // 供应商企业名
        ProfileInfoDTO supplier = profileMap.get(c.getSupplierTenantId());
        if (supplier != null) {
            vo.setSupplierCompanyName(supplier.getCompanyName());
        }
        // 联系方式仅已接受状态返回
        if (ConnectionStatus.ACCEPTED.getCode().equals(c.getStatus())) {
            if (buyer != null) {
                vo.setBuyerContactName(buyer.getContactName());
                vo.setBuyerContactPhone(buyer.getContactPhone());
                vo.setBuyerContactEmail(buyer.getContactEmail());
            }
            if (supplier != null) {
                vo.setSupplierContactName(supplier.getContactName());
                vo.setSupplierContactPhone(supplier.getContactPhone());
                vo.setSupplierContactEmail(supplier.getContactEmail());
            }
        }
        return vo;
    }
}
