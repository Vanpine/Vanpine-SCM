package cn.greensmartplanet.gsc.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 流式对话专用线程池。
 * <p>
 * 为什么不复用框架的 {@code gscAsyncExecutor}：
 * <ul>
 *   <li><b>任务时长差两个数量级</b>：流式对话单个任务最长 120s（等 Python 流式返回），
 *       压缩/图片描述是秒级。混在一个池里，几个长流就能把短任务饿死。</li>
 *   <li><b>拒绝策略必须不同</b>：框架池用 CallerRunsPolicy（旁路任务不能丢），
 *       但流式任务一旦落到 tomcat 请求线程，就会把请求线程占满 120s——
 *       线程耗尽后整个服务不可用。这里必须快速失败。</li>
 * </ul>
 * 容量按「并发对话数」定，而不是按后台任务定：每个流占一个线程，这是阻塞式读下游 SSE
 * 的固有代价，无法靠队列消化——用户已经在等回答，排队没有意义。
 *
 * @author gsc
 */
@Slf4j
@Configuration
public class AiStreamExecutorConfig {

    /** Bean 名：{@code @Async("aiStreamExecutor")} 引用 */
    public static final String STREAM_EXECUTOR_BEAN = "aiStreamExecutor";

    /** 常驻线程数：覆盖日常并发对话 */
    @Value("${gsc.ai.stream.core-size:16}")
    private int coreSize;

    /** 峰值线程数：突发对话时扩容上限（每个线程对应一个在途会话） */
    @Value("${gsc.ai.stream.max-size:64}")
    private int maxSize;

    /**
     * 流式对话专用执行器。
     * <p>
     * queue=0 + AbortPolicy：池满立即抛 {@code TaskRejectedException}，
     * 由 {@code AiChatController} 捕获后给前端一个「服务繁忙」的明确提示——
     * 比默默把 tomcat 线程拖住 120s 好得多。
     */
    @Bean(name = STREAM_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor aiStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 容量：按并发会话数定，不排队
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(Math.max(maxSize, coreSize));
        executor.setQueueCapacity(0);
        // 2. 线程名可区分，日志里能一眼认出是流式线程
        executor.setThreadNamePrefix("ai-stream-");
        // 3. 池满快速失败，绝不落到 tomcat 请求线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 4. 停机不等待：一个流最长 120s，等它会把优雅停机拖死；前端会收到断流并重连
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        log.info("AI 流式线程池初始化: core={}, max={}, queue=0（池满快速失败）", coreSize, maxSize);
        return executor;
    }
}
