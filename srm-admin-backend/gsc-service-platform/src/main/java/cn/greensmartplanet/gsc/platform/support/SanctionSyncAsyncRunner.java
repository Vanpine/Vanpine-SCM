package cn.greensmartplanet.gsc.platform.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行器 — 把任务交给 Spring 线程池后台执行。
 *
 * <p>存在意义：{@code @Async} 依赖 Spring AOP 代理生效，**同类内 {@code this.xxx()} 自调用会绕过代理、
 * 静默退化为同步执行**。把 {@code @Async} 方法放在独立 Bean 上，调用方经代理调用才真正异步。
 *
 * <p>典型场景：制裁名单同步耗时 1-3 分钟（下载 70MB CSV + 解析 + 入库），
 * 手动触发接口必须在 10s 内返回（网关 response-timeout），不能同步阻塞。
 *
 * @author gsc
 */
@Slf4j
@Component
public class SanctionSyncAsyncRunner {

    /**
     * 后台执行任务（经 Spring 代理，@Async 生效）
     *
     * @param task 待执行任务；异常由任务自身兜住（本方法不做捕获，避免掩盖业务异常）
     */
    @Async
    public void run(Runnable task) {
        task.run();
    }
}
