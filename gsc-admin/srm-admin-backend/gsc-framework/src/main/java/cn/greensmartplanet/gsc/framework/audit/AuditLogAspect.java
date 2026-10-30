package cn.greensmartplanet.gsc.framework.audit;

import cn.greensmartplanet.gsc.framework.security.JwtUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志 AOP 切面 — 拦截带 @AuditLog 注解的 Controller 方法，异步写入审计日志
 * <p>
 * 落库通道由各服务注入 {@link AuditLogSink} 实现决定：
 * 旧单体/平台服务直写 audit_log；其他服务发 MQ 事件。
 * 使用 ObjectProvider 延迟解析，避免无 sink 实现的服务启动失败。
 *
 * @author gsc
 */
@Slf4j
@Aspect
public class AuditLogAspect {

    private final ObjectProvider<AuditLogSink> sinkProvider;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(ObjectProvider<AuditLogSink> sinkProvider, ObjectMapper objectMapper) {
        this.sinkProvider = sinkProvider;
        this.objectMapper = objectMapper;
    }

    /** 拦截所有带 @AuditLog 注解的方法 */
    @Around("@annotation(cn.greensmartplanet.gsc.framework.audit.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AuditLog auditLog = signature.getMethod().getAnnotation(AuditLog.class);
        // 2. 取当前登录用户
        JwtUserDetails user = getCurrentUser();
        // 3. 取请求上下文
        HttpServletRequest request = getRequest();
        // 4. 计时
        long startTime = System.currentTimeMillis();

        // 5. 先从请求参数提取 targetId（PUT/DELETE 的路径参数）
        String targetType = auditLog.targetType();
        Long targetId = extractTargetIdFromParams(joinPoint);

        // 6. 构建日志实体
        AuditLogEntry entry = AuditLogEntry.builder()
                .module(auditLog.module())
                .operation(auditLog.operation())
                .description(auditLog.desc())
                .tenantId(user != null ? user.getTenantId() : null)
                .userId(user != null ? user.getUserId() : null)
                .username(user != null ? user.getUsername() : null)
                .requestMethod(request != null ? request.getMethod() : null)
                .requestUrl(request != null ? request.getRequestURI() : null)
                .requestParams(buildParams(joinPoint))
                .ip(getClientIp(request))
                .targetType(targetType)
                .targetId(targetId)
                .createTime(LocalDateTime.now())
                .build();

        try {
            // 7. 执行目标方法
            Object result = joinPoint.proceed();

            // 8. 从返回值补充 targetId + targetSummary（CREATE 操作的 ID 在返回值里）
            Object data = extractData(result);
            if (targetId == null && data != null) {
                targetId = extractTargetIdFromVO(data);
                entry.setTargetId(targetId);
            }
            if (data != null) {
                entry.setTargetSummary(buildTargetSummary(targetType, data));
            }

            // 9. 成功
            entry.setStatus("success");
            entry.setCostTimeMs((int) (System.currentTimeMillis() - startTime));
            save(entry);
            return result;
        } catch (Throwable e) {
            // 10. 失败
            entry.setStatus("fail");
            entry.setErrorMsg(truncate(e.getMessage(), 1024));
            entry.setCostTimeMs((int) (System.currentTimeMillis() - startTime));
            save(entry);
            throw e;
        }
    }

    /** 通过 sink 落库；无实现时仅告警，绝不阻断业务（审计不阻塞主流程） */
    private void save(AuditLogEntry entry) {
        try {
            AuditLogSink sink = sinkProvider.getIfAvailable();
            if (sink != null) {
                sink.save(entry);
            } else {
                log.warn("未配置 AuditLogSink 实现，审计日志丢弃: module={}, operation={}", entry.getModule(), entry.getOperation());
            }
        } catch (Exception e) {
            log.error("审计日志落库失败（不影响业务）: {}", e.getMessage());
        }
    }

    /** 从方法参数中提取目标ID（优先 @PathVariable id，其次第一个 Long 参数） */
    private Long extractTargetIdFromParams(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames == null || args == null) return null;
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            if ("id".equals(paramNames[i]) && args[i] instanceof Long lid) return lid;
        }
        for (Object arg : args) {
            if (arg instanceof Long lid) return lid;
        }
        return null;
    }

    /** 从 R<T> 中取出 data */
    private Object extractData(Object result) {
        if (result == null) return null;
        try {
            Method getData = result.getClass().getMethod("getData");
            return getData.invoke(result);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 VO 对象中提取 ID */
    private Long extractTargetIdFromVO(Object vo) {
        if (vo == null) return null;
        try {
            Method getId = vo.getClass().getMethod("getId");
            Object id = getId.invoke(vo);
            return id instanceof Long lid ? lid : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 VO 对象中提取可读字段构建摘要 */
    private String buildTargetSummary(String targetType, Object vo) {
        if (vo == null) return null;
        // 按优先级找可读字段
        String[] summaryFields = {"inquiryNo", "quoteNo", "contractNo", "title", "companyName", "tenantName", "roleName",
                "menuName", "username", "realName", "firmName"};
        String label = null;
        for (String field : summaryFields) {
            label = getStringField(vo, field);
            if (label != null && !label.isBlank()) break;
        }
        Long id = extractTargetIdFromVO(vo);
        if (id == null && label == null) return null;
        if (label != null) {
            return (targetType != null && !targetType.isEmpty() ? targetType + " " : "") + label;
        }
        return (targetType != null && !targetType.isEmpty() ? targetType + " #" + id : "#" + id);
    }

    private String getStringField(Object obj, String fieldName) {
        try {
            Method getter = obj.getClass().getMethod("get" + Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1));
            Object val = getter.invoke(obj);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取当前登录用户 */
    private JwtUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtUserDetails user) {
            return user;
        }
        return null;
    }

    /** 获取当前请求对象 */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /** 构建请求参数JSON（排除不可序列化对象） */
    private String buildParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) return null;
            var safeArgs = Arrays.stream(args)
                    .filter(a -> !(a instanceof HttpServletRequest)
                            && !(a instanceof jakarta.servlet.http.HttpServletResponse)
                            && !(a instanceof JwtUserDetails))
                    .collect(Collectors.toList());
            if (safeArgs.isEmpty()) return null;
            String json = objectMapper.writeValueAsString(safeArgs);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (Exception e) {
            return null;
        }
    }

    /** 获取客户端真实IP */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 截断字符串 */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
