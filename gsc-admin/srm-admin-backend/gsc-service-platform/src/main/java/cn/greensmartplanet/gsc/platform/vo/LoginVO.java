package cn.greensmartplanet.gsc.platform.vo;

import cn.greensmartplanet.gsc.platform.vo.MenuTreeVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应 — 包含 Token、用户信息、菜单
 *
 * @author gsc
 */
@Data
@Builder
@Schema(description = "登录响应")
public class LoginVO {

    @Schema(description = "访问Token")
    private String accessToken;

    @Schema(description = "刷新Token")
    private String refreshToken;

    @Schema(description = "Token有效期（秒）")
    private long expiresIn;

    @Schema(description = "用户信息")
    private UserInfoVO userInfo;

    @Schema(description = "菜单树")
    private List<MenuTreeVO> menus;
}
