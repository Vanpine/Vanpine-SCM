package cn.greensmartplanet.gsc.trade.service.impl;

import cn.greensmartplanet.gsc.api.supplier.ConnectionClient;
import cn.greensmartplanet.gsc.api.supplier.dto.MyConnectionStatusDTO;
import cn.greensmartplanet.gsc.common.enums.ConnectionStatus;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import cn.greensmartplanet.gsc.trade.mapper.ProcurementRequirementMapper;
import cn.greensmartplanet.gsc.trade.service.RequirementQueryService;
import cn.greensmartplanet.gsc.trade.support.ProfileNameHelper;
import cn.greensmartplanet.gsc.trade.vo.RequirementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购需求查询服务实现 — 我的需求 / 需求详情 / 需求广场
 * <p>
 * 只读查询无 @Transactional；需求广场企业名/对接数/对接状态均批量 Feign 查询后组装（红线 1，消 N+1）。
 *
 * @author gsc
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementQueryServiceImpl implements RequirementQueryService {

    private final ProcurementRequirementMapper requirementMapper;
    // 对接归供应商域，Feign 查询（对接数/我的对接状态）
    private final ConnectionClient connectionClient;
    private final ProfileNameHelper profileNameHelper;

    /**
     * 分页查我的需求（先扫描过期），批量回填对接数
     */
    @Override
    public Map<String, Object> listMyRequirements(Long tenantId, int pageNum, int pageSize, String status, String keyword) {
        // 1. 查询前兜底扫描过期（定时任务 + 查询兜底双跑，UPDATE 幂等）
        requirementMapper.updateExpired();
        int offset = (pageNum - 1) * pageSize;
        List<ProcurementRequirement> list = requirementMapper.selectMyPage(tenantId, status, keyword, offset, pageSize);
        long total = requirementMapper.countMy(tenantId, status, keyword);

        // 2. Entity → VO（含动态过期判定）
        List<RequirementVO> records = list.stream()
                .map(RequirementVO::fromEntity)
                .toList();

        // 3. 批量查对接数并回填（对接归供应商域，Feign 批量查询）
        if (!records.isEmpty()) {
            List<Long> reqIds = records.stream().map(RequirementVO::getId).toList();
            Map<Long, Long> countMap = connectionClient
                    .countGroupByRequirementIds(tenantId, reqIds).getData();
            for (RequirementVO vo : records) {
                Long cnt = countMap.get(vo.getId());
                vo.setConnectionCount(cnt != null ? cnt.intValue() : 0);
            }
        }

        // 4. 组装分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 需求详情：非发布方且非公开需求拒绝访问（红线 2）
     */
    @Override
    public RequirementVO getDetail(Long id, Long tenantId) {
        // 1. 查询前兜底扫描过期（定时任务 + 查询兜底双跑，UPDATE 幂等）
        requirementMapper.updateExpired();
        // 2. 需求必须存在
        ProcurementRequirement req = requirementMapper.selectById(id);
        if (req == null) {
            throw new BizException(ErrorCode.MATCH_REQUIREMENT_NOT_FOUND);
        }
        // 3. 权限校验（红线 2）：非发布方且非公开需求拒绝访问
        if (!tenantId.equals(req.getTenantId()) && (req.getIsPublic() == null || !req.getIsPublic())) {
            throw new BizException(ErrorCode.MATCH_NOT_AUTHORIZED);
        }
        return RequirementVO.fromEntity(req);
    }

    /**
     * 需求广场：跨租户查 open 需求（排除自己），附发布方企业名、对接数、我的对接状态
     */
    @Override
    public Map<String, Object> listOpenRequirements(Long excludeTenantId, String keyword, int pageNum, int pageSize) {
        // 1. 查询前兜底扫描过期
        requirementMapper.updateExpired();
        int offset = (pageNum - 1) * pageSize;
        // 2. 跨租户查所有 open 需求（排除自己发布的，支持品类关键词搜索）
        List<ProcurementRequirement> list = requirementMapper.selectOpenListExcludeTenant(excludeTenantId, keyword, offset, pageSize);
        long total = requirementMapper.countOpenExcludeTenant(excludeTenantId, keyword);

        // 3. Entity → VO，附发布方企业名（画像归供应商域，批量查询消 N+1）
        List<Long> publisherIds = list.stream().map(ProcurementRequirement::getTenantId).distinct().toList();
        Map<Long, String> companyMap = profileNameHelper.getCompanyNames(publisherIds);
        List<RequirementVO> records = list.stream()
                .map(r -> {
                    RequirementVO vo = RequirementVO.fromEntity(r);
                    vo.setBuyerCompanyName(companyMap.get(r.getTenantId()));
                    return vo;
                })
                .toList();

        // 4. 批量查对接数（公开需求展示用）+ 回填当前供应商的对接状态（对接归供应商域，Feign 批量查询）
        if (!records.isEmpty()) {
            List<Long> reqIds = records.stream().map(RequirementVO::getId).toList();
            Map<Long, Long> countMap = connectionClient
                    .countGroupByRequirementIds(excludeTenantId, reqIds).getData();
            for (RequirementVO vo : records) {
                Long cnt = countMap.get(vo.getId());
                vo.setConnectionCount(cnt != null ? cnt.intValue() : 0);
            }

            Map<Long, MyConnectionStatusDTO> myStatusMap = connectionClient
                    .getMyConnectionStatus(excludeTenantId, reqIds).getData();
            for (RequirementVO vo : records) {
                MyConnectionStatusDTO my = myStatusMap.get(vo.getId());
                if (my != null) {
                    vo.setMyConnectionStatus(my.getStatus());
                    if (ConnectionStatus.REJECTED.getCode().equals(my.getStatus())) {
                        vo.setMyRejectReason(my.getReplyMessage());
                    }
                }
            }
        }

        // 5. 组装分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }
}
