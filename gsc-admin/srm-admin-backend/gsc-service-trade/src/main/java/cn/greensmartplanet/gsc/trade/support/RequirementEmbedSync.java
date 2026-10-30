package cn.greensmartplanet.gsc.trade.support;

import cn.greensmartplanet.gsc.common.enums.RequirementStatus;
import cn.greensmartplanet.gsc.trade.entity.ProcurementRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开需求写入 Qdrant open_requirements。发布/改内容后 upsert，关闭或非公开则删除。
 * 失败只打日志，不挡需求主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementEmbedSync {

    private final RestClient aiRestClient;

    /** 事务提交后再同步，避免回滚后向量库脏数据 */
    public void scheduleSync(ProcurementRequirement req) {
        if (req == null || req.getId() == null) {
            return;
        }
        Long id = req.getId();
        boolean keep = RequirementStatus.OPEN.getCode().equals(req.getStatus())
                && Boolean.TRUE.equals(req.getIsPublic());
        Map<String, Object> body = keep ? toEmbedBody(req) : null;
        runAfterCommit(() -> {
            if (keep) {
                upsert(id, body);
            } else {
                delete(id);
            }
        });
    }

    private void upsert(Long requirementId, Map<String, Object> body) {
        try {
            aiRestClient.post()
                    .uri("/api/embed-requirement")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("需求向量已同步: requirementId={}", requirementId);
        } catch (Exception e) {
            log.warn("需求向量同步失败: requirementId={}, error={}", requirementId, e.getMessage());
        }
    }

    private void delete(Long requirementId) {
        try {
            aiRestClient.delete()
                    .uri("/api/embed-requirement/{id}", requirementId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("需求向量已删除: requirementId={}", requirementId);
        } catch (Exception e) {
            log.warn("需求向量删除失败: requirementId={}, error={}", requirementId, e.getMessage());
        }
    }

    private Map<String, Object> toEmbedBody(ProcurementRequirement req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requirement_id", req.getId());
        body.put("title", nullToEmpty(req.getTitle()));
        body.put("category", nullToEmpty(req.getCategory()));
        body.put("description", nullToEmpty(req.getDescription()));
        body.put("cert_require", nullToEmpty(req.getCertRequire()));
        body.put("region", nullToEmpty(req.getRegion()));
        body.put("tenant_id", req.getTenantId());
        return body;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
