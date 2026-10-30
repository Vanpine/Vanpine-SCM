package cn.greensmartplanet.gsc.framework.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 护照用户信息 — PassportClient 调用 API 返回的数据对象
 *
 * @author gsc
 */
@Data
@Builder
public class PassportUserInfo {
    /** AI护照唯一标识 */
    private String uuid;
    /** 护照用户名 */
    private String username;
    /** 护照绑定的邮箱 */
    private String email;
    /** 护照绑定的手机号 */
    private String phone;
    /** 护照实名认证的真实姓名 */
    private String realName;
    /** 是否已通过实名认证（true=已实名, false=未实名） */
    private boolean idVerified;
}
