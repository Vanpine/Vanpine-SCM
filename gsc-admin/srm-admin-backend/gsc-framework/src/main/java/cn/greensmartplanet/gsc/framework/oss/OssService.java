package cn.greensmartplanet.gsc.framework.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 上传服务
 * 用于头像、文件等上传到 OSS 并返回访问 URL
 * <p>
 * 微服务拆分：由 GscFrameworkAutoConfiguration 注册（配置 gsc.oss.* 才生效）。
 * <p>
 * <b>存储约定（重要）</b>：{@link #upload} 返回的是 <b>object key</b>（对象路径），不是可访问 URL。
 * 调用方应把 key 落库（如 oss_key 列），需要访问时再用 {@link #generateGetUrl} 现签短效 URL。
 * 切勿把签名 URL 当永久地址持久化——签名会过期，届时无法访问且无法重新签名。
 *
 * @author gsc
 */
@Slf4j
public class OssService {

    /** 默认预签名有效期：10 分钟（仅供页面即时预览/下载，用完即弃） */
    public static final long DEFAULT_EXPIRE_SECONDS = 600;

    @Value("${gsc.oss.endpoint}")
    private String endpoint;

    @Value("${gsc.oss.bucket}")
    private String bucket;

    @Value("${gsc.oss.access-key-id}")
    private String accessKeyId;

    @Value("${gsc.oss.access-key-secret}")
    private String accessKeySecret;

    /**
     * OSS 自定义域名（CDN 加速域名），可选。
     * <p>
     * <b>为什么需要它</b>：阿里云对 OSS 默认域名（*.oss-cn-*.aliyuncs.com）强制附加
     * {@code Content-Disposition: attachment}，导致浏览器在 iframe/新窗口里一律触发下载，
     * 无法内联预览 PDF/文本。换成绑定的自定义域名后即可正常内联渲染。
     * <p>
     * 未配置时保持默认域名（预览会退化为下载，但不影响其它功能）。
     */
    @Value("${gsc.oss.custom-domain:}")
    private String customDomain;

    /** OSS 默认域名的 host 前缀（用于识别并替换为自定义域名） */
    private String defaultHost;

    private OSS client;

    /** 文件扩展名 → Content-Type 映射，让浏览器能正确预览 */
    private static final Map<String, String> CONTENT_TYPE_MAP = new HashMap<>() {{
        put(".pdf", "application/pdf");
        put(".jpg", "image/jpeg");
        put(".jpeg", "image/jpeg");
        put(".png", "image/png");
        put(".gif", "image/gif");
        put(".doc", "application/msword");
        put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        put(".xls", "application/vnd.ms-excel");
        put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        put(".txt", "text/plain");
        put(".md", "text/markdown");
    }};

    /** 初始化 OSS Client */
    @PostConstruct
    public void init() {
        client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        // 提取默认域名 host（如 srm-system.oss-cn-guangzhou.aliyuncs.com），供签名后替换
        defaultHost = endpoint.replaceFirst("^https?://", "") .replaceFirst("/$", "");
        if (bucket != null && !bucket.isBlank() && !defaultHost.startsWith(bucket + ".")) {
            defaultHost = bucket + "." + defaultHost;
        }
        log.info("OSS Client 初始化完成, endpoint={}, bucket={}", endpoint, bucket);
    }

    /** 销毁时关闭 Client */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    /**
     * 上传文件到 OSS
     *
     * @param file 上传的文件
     * @param dir  存储目录（如 avatar、document）
     * @return object key（对象路径），调用方应落库存 key，而非签名 URL
     */
    public String upload(MultipartFile file, String dir) throws IOException {
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        // key = 目录/日期/UUID/原始文件名：UUID 放目录层保证唯一，URL 尾段是原始名（预览标题不再是无意义 UUID）
        String base = sanitizeKeyName(originalName);
        if (base.endsWith(ext)) {
            base = base.substring(0, base.length() - ext.length());
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String key = dir + "/" + date + "/" + uid + "/" + base + ext;
        doUpload(key, file.getInputStream(), ext);
        return key;
    }

    /**
     * 上传字节数组到 OSS（供 PDF 生成等非前端上传场景使用）
     *
     * @param bytes    文件字节数组
     * @param dir      存储目录
     * @param fileName 文件名（用于提取扩展名）
     * @return object key（对象路径），调用方应落库存 key，而非签名 URL
     */
    public String upload(byte[] bytes, String dir, String fileName) throws IOException {
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf("."));
        }
        // key = 目录/日期/UUID/原始文件名：UUID 放目录层保证唯一，URL 尾段是原始名（预览标题不再是无意义 UUID）
        String base = sanitizeKeyName(fileName);
        if (base.endsWith(ext)) {
            base = base.substring(0, base.length() - ext.length());
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String key = dir + "/" + date + "/" + uid + "/" + base + ext;
        doUpload(key, new java.io.ByteArrayInputStream(bytes), ext);
        return key;
    }

    /**
     * 按 object key 生成短效预签名 GET URL（供前端即时预览/下载）。
     * <p>
     * 每次调用都重新签名，因此不会有过期之忧；URL 本身是 bearer 凭证，
     * 时效应尽量短，且只在通过权限校验后才下发给前端。
     *
     * @param key object key（上传时返回的对象路径）
     * @return 带签名的可访问 URL
     */
    public String generateGetUrl(String key) {
        return generateGetUrl(key, DEFAULT_EXPIRE_SECONDS);
    }

    /**
     * 按 object key 生成指定时效的预签名 GET URL。
     *
     * @param key           object key
     * @param expireSeconds 有效期（秒），建议 300~900
     * @return 带签名的可访问 URL
     */
    public String generateGetUrl(String key, long expireSeconds) {
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        // 只签名最基础的 GET：不附加 response-* 覆盖参数。
        // 原因：response-content-disposition 等参数会进入待签名串，而浏览器最终请求的 URL
        // 未必带上它（中文 key 经 URL-encode 后参数易被吞），导致 SignatureDoesNotMatch。
        // PDF / txt / md 默认就是内联渲染，无需该头。
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key, HttpMethod.GET);
        request.setExpiration(expiration);
        String url = client.generatePresignedUrl(request).toString();
        // 换自定义域名：OSS 默认域名强制 Content-Disposition: attachment，iframe 无法内联预览。
        // 签名 V2 不绑定域名，替换 host 后签名依然有效（与前端 normalizeOssUrl 同一策略）。
        if (customDomain != null && !customDomain.isBlank() && defaultHost != null && !defaultHost.isBlank()) {
            url = url.replace(defaultHost, customDomain.replaceFirst("^https?://", "").replaceFirst("/$", ""));
        }
        return url;
    }

    /**
     * 删除 OSS 对象（仅调用方确认业务上确实要物理删除时才调用）。
     * <p>
     * 删除不存在的 key 不会抛异常（OSS deleteObject 幂等）。
     *
     * @param key object key
     */
    public void deleteObject(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(bucket, key);
            log.info("OSS 对象已删除, key={}", key);
        } catch (OSSException e) {
            log.error("OSS 删除失败, key={}, code={}, message={}", key, e.getErrorCode(), e.getErrorMessage(), e);
            throw new RuntimeException("OSS 删除失败: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("OSS 删除客户端异常, key={}, message={}", key, e.getMessage(), e);
            throw new RuntimeException("OSS 删除客户端异常: " + e.getMessage(), e);
        }
    }

    /** 清洗为合法的 OSS key 文件名片段（去路径分隔符与非法字符，中文保留） */
    private String sanitizeKeyName(String name) {
        if (name == null || name.isBlank()) return "file";
        String cleaned = name.trim()
                .replaceAll("[/\\\\:*?\"<>|\\s]+", "_")
                .replaceAll("_+", "_");
        return cleaned.startsWith("_") ? cleaned.substring(1) : cleaned;
    }

    /** 执行上传 */
    private void doUpload(String key, java.io.InputStream inputStream, String ext) throws IOException {
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(CONTENT_TYPE_MAP.getOrDefault(ext.toLowerCase(), "application/octet-stream"));
            client.putObject(bucket, key, inputStream, meta);
        } catch (OSSException e) {
            log.error("OSS 上传失败, code={}, message={}", e.getErrorCode(), e.getErrorMessage(), e);
            throw new IOException("OSS 上传失败: " + e.getErrorMessage(), e);
        } catch (ClientException e) {
            log.error("OSS 客户端异常, message={}", e.getMessage(), e);
            throw new IOException("OSS 客户端异常: " + e.getMessage(), e);
        }
    }
}
