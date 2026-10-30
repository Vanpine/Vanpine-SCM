package cn.greensmartplanet.gsc.api.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业画像信息契约 DTO — 供应商域内部接口返回给其他域的画像字段子集
 * <p>
 * 使用场景（Feign 契约）：
 * <ul>
 *   <li>交易域：发布需求校验画像身份 + 需求联系人默认值回填；通知文案企业名</li>
 *   <li>交付域：通知文案企业名</li>
 *   <li>平台域：角色分配按画像身份过滤；尽调确认通过后触发画像初始化</li>
 * </ul>
 *
 * @author gsc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileInfoDTO {

    /** 租户ID */
    private Long tenantId;
    /** 公司名称 */
    private String companyName;
    /** 画像类型：buyer / supplier / both */
    private String profileType;
    /** 联系人 */
    private String contactName;
    /** 联系电话 */
    private String contactPhone;
    /** 联系邮箱 */
    private String contactEmail;
    /** 法定代表人 */
    private String legalRep;
    /** 注册地址 */
    private String registeredAddress;
    /** 所在国家 */
    private String country;
    /** 省份 */
    private String province;
    /** 城市 */
    private String city;
    /** 画像完整度评分（0-100） */
    private Integer completenessScore;
}
