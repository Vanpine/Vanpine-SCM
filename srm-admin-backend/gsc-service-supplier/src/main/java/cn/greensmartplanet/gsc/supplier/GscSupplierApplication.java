package cn.greensmartplanet.gsc.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * 供应商域服务启动入口
 * <p>
 * 扫描范围：本包（gsc.supplier）+ gsc.api 契约模块的 FallbackFactory。
 * ⚠️ 注意：@ComponentScan 显式声明会覆盖 @SpringBootApplication 默认扫描，
 * 必须把本服务包与 gsc.api 同时列出，否则业务 Controller/Service 不会注册。
 * gsc-framework 的 Bean 由 GscFrameworkAutoConfiguration 自动装配提供（含 MqAuditLogSink 审计落库）。
 * @EnableFeignClients：gsc-api 契约模块的 Feign 客户端（平台域入驻申请信息查询）。
 *
 * @author gsc
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients("cn.greensmartplanet.gsc.api")
@MapperScan("cn.greensmartplanet.gsc.supplier.mapper")
@ComponentScan({"cn.greensmartplanet.gsc.supplier", "cn.greensmartplanet.gsc.api"})
public class GscSupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(GscSupplierApplication.class, args);
    }
}
