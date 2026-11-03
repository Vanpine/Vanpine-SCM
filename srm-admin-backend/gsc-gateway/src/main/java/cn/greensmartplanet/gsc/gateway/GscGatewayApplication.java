package cn.greensmartplanet.gsc.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * GSC 统一网关启动入口
 * <p>
 * 监听 8080（与原单体同端口，前端零改动），负责 JWT 校验、X-Tenant-Id 注入、路由、限流。
 *
 * @author gsc
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GscGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GscGatewayApplication.class, args);
    }
}
