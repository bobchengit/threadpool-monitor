package io.itbob.threadpool.provider.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 3 个演示线程池，参数与队列容量各不相同：
 * - orderExecutor：主展示池，正弦波动明显
 * - reportExecutor：中等负载
 * - alarmExecutor：小队列（容量 50），burst 模式下容易触发 >80% 告警
 */
@Configuration
public class ExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor orderExecutor() {
        return buildExecutor("order-", 4, 8, 200);
    }

    @Bean
    public ThreadPoolTaskExecutor reportExecutor() {
        return buildExecutor("report-", 2, 4, 100);
    }

    @Bean
    public ThreadPoolTaskExecutor alarmExecutor() {
        return buildExecutor("alarm-", 2, 2, 50);
    }

    private ThreadPoolTaskExecutor buildExecutor(String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(prefix);
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
