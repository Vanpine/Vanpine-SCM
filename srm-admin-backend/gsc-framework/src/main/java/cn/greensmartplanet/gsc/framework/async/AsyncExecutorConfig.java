package cn.greensmartplanet.gsc.framework.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Async 专用线程池。
 * <p>
 * 为什么必须自定义：{@code @EnableAsync} 未指定 executor 时，Spring 用 {@code SimpleAsyncTaskExecutor}——
 * 每个任务新建一个线程、无上限。AI 压缩 / 量刑名单同步 / 画像同步都是「阻塞等外部 HTTP」的任务，
 * 一次并发几十个就等于几十个线程各等 60 秒；极端情况下线程数随请求量线性上涨。
 * <p>
 * 配置（Nacos 可调，均有默认值）：
 * <ul>
 *   <li>{@code gsc.async.core-size}：常驻线程数，默认 4</li>
 *   <li>{@code gsc.async.max-size}：峰值线程数，默认 16</li>
 *   <li>{@code gsc.async.queue-capacity}：队列长度，默认 200</li>
 * </ul>
 * 拒绝策略用 {@link ThreadPoolExecutor.CallerRunsPolicy}：队列满时由提交线程自己执行，
 * 起到反压作用——不会丢任务（这些任务多数是旁路，丢了只是延后，但静默丢反而难排查）。
 *
 * @author gsc
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncExecutorConfig implements AsyncConfigurer {

    /** Bean 名：@Async 不指定名字时走 AsyncConfigurer#getAsyncExecutor 返回的默认池 */
    public static final String ASYNC_EXECUTOR_BEAN = "gscAsyncExecutor";

    @Value("${gsc.async.core-size:4}")
    private int coreSize;

    @Value("${gsc.async.max-size:16}")
    private int maxSize;

    @Value("${gsc.async.queue-capacity:200}")
    private int queueCapacity;

    /**
     * @Async 默认线程池（覆盖 Spring 的 SimpleAsyncTaskExecutor）。
     */
    /**
     * {@code @Async} 的默认执行器：直接复用上面注册的 Bean，避免两个线程池。
     */
    @Override
    public Executor getAsyncExecutor() {
        return gscAsyncExecutor();
    }

    /**
     * 压缩 / 同步等后台任务的专用线程池，同时作为 {@code @Async} 的默认执行器。
     * <p>
     * 注册为 {@link AsyncConfigurer}（实现 {@code getAsyncExecutor}）后，Spring 会用它替换
     * 默认的 {@code SimpleAsyncTaskExecutor}；这里再显式声明一个同类型 Bean 便于其它组件按名注入。
     */
    @Bean(name = ASYNC_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor gscAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 线程容量：core 常驻，max 应对突发；外部 HTTP 型任务不宜太多，避免打满下游
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(Math.max(maxSize, coreSize));
        executor.setQueueCapacity(queueCapacity);
        // 2. 线程名带前缀，日志里能一眼认出是不是异步线程（便于排查「任务没跑」）
        executor.setThreadNamePrefix("gsc-async-");
        // 3. 队列满时反压到提交线程，不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 4. 优雅停机：等待在途任务，避免压缩/同步被硬杀留下半截状态
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("异步线程池初始化: core={}, max={}, queue={}", coreSize, maxSize, queueCapacity);
        return executor;
    }
}
