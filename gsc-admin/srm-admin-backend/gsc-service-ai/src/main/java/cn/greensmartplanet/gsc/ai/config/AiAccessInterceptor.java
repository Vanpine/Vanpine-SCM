package cn.greensmartplanet.gsc.ai.config;

import cn.greensmartplanet.gsc.common.constant.SystemConstants;
import cn.greensmartplanet.gsc.common.exception.BizException;
import cn.greensmartplanet.gsc.common.exception.ErrorCode;
import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * AI 接口访问拦截：尽调预审用户、事务所不能使用对话 / 知识库。
 * 挂在请求线程上执行，避免流式对话 @Async 后拿不到 SecurityContext。
 *
 * @author gsc
 */
@Component
public class AiAccessInterceptor implements HandlerInterceptor {

    /**
     * 请求进入 /api/admin/ai/** 时校验角色。
     *
     * @return true 放行
     * @throws BizException 尽调或事务所角色访问 AI
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails user)) {
            return true;
        }
        List<String> roles = user.getRoles();
        if (roles != null && (roles.contains(SystemConstants.ROLE_DUE_DILIGENCE)
                || roles.contains(SystemConstants.ROLE_FIRM))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
