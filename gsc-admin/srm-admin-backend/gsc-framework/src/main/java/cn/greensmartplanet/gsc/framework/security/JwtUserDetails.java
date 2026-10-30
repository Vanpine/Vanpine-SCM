package cn.greensmartplanet.gsc.framework.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * JWT 用户详情 — 适配 Spring Security 的 UserDetails 接口
 *
 * @author gsc
 */
@Getter
@Builder
public class JwtUserDetails implements UserDetails {

    private Long userId;                       // 用户ID
    private Long tenantId;                     // 所属租户ID
    private String tenantCode;                 // 所属租户编码
    private String username;                   // 用户名
    private Long firmId;                       // 事务所ID（FIRM 角色才有）
    /** JWT 中的角色编码，如 TENANT_ADMIN / DUE_DILIGENCE / FIRM */
    private List<String> roles;
    private Collection<? extends GrantedAuthority> authorities; // 权限列表

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;  // JWT 方式不需要密码
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
