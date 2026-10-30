package cn.greensmartplanet.gsc.platform.vo;

import cn.greensmartplanet.gsc.framework.oss.OssService;
import cn.greensmartplanet.gsc.platform.entity.DueDiligenceAttachment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 尽调附件材料 VO（多值附件中的一项）
 * <p>
 * 库里存 oss_key，返回给前端时现签为 url —— 签名会过期，不能持久化。
 *
 * <p>⚠️ 迁移期桥接（微服务拆分）：本文件归属供应商域，platform 内为过渡副本。</p>
 *
 * @author gsc
 */
@Data
@Schema(description = "尽调附件材料")
public class DueDiligenceAttachmentVO {

    @Schema(description = "附件ID")
    private Long id;

    @Schema(description = "访问 URL（由 ossKey 现签）")
    private String url;

    @Schema(description = "OSS 对象路径（供编辑回填后再次提交，非签名 URL）")
    private String ossKey;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "排序")
    private Integer sortOrder;

    /**
     * 实体 → VO：按 oss_key 现签短效 URL。
     *
     * @param a          附件实体
     * @param ossService OSS 服务；为 null 时 url 取原 key（不签名）
     */
    public static DueDiligenceAttachmentVO of(DueDiligenceAttachment a, OssService ossService) {
        DueDiligenceAttachmentVO vo = new DueDiligenceAttachmentVO();
        vo.setId(a.getId());
        vo.setFileName(a.getFileName());
        vo.setSortOrder(a.getSortOrder());
        String key = a.getOssKey();
        if (key != null && !key.isBlank()) {
            vo.setUrl(ossService != null ? ossService.generateGetUrl(key) : key);
            // 同时回传原始 key，前端编辑时回填可直接再次提交
            vo.setOssKey(key);
        }
        return vo;
    }
}
