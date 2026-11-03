package cn.greensmartplanet.gsc.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 域服务启动入口
 * <p>
 * 职责：AI 对话机器人的数据面（会话/消息/知识库文档 CRUD）+ 流式代理 Python RAG 服务。
 * <p>
 * 扫描范围：本包（gsc.ai）+ gsc.api 契约模块的 FallbackFactory。
 * gsc-framework 的 Bean（JWT/安全/租户/审计/OSS）由 GscFrameworkAutoConfiguration 自动装配提供。
 * 审计落库走 MqAuditLogSink（发 gsc-audit-log 事件，平台域唯一落库方），无需本服务自建 sink。
 *
 * @author gsc
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients("cn.greensmartplanet.gsc.api")
@MapperScan("cn.greensmartplanet.gsc.ai.mapper")
@ComponentScan({"cn.greensmartplanet.gsc.ai", "cn.greensmartplanet.gsc.api"})
public class GscAiApplication {

    /**
     * 应用启动入口。
     *
     * @param args 命令行参数（当前服务不使用，保留 Spring Boot 标准签名）
     */
    public static void main(String[] args) {
        SpringApplication.run(GscAiApplication.class, args);
    }
}
