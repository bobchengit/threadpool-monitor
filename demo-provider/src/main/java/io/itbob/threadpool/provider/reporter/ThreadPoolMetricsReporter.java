package io.itbob.threadpool.provider.reporter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * ★ 参考接入组件（第三方系统拷走即用）★
 *
 * 将本类整体复制到第三方工程（如 kemov-spdMgr）中，与原 ThreadPoolMonitor.java 同级，
 * 即可把"定时打印日志"升级为"暴露 HTTP 指标接口供监控中心拉取"，无需其他改动。
 *
 * 依赖：spring-web（@RestController）、slf4j——第三方工程本就具备，零新增依赖。
 * 指标口径与原 ThreadPoolMonitor.java 完全一致：
 * 遍历容器中所有 ThreadPoolTaskExecutor，取核心线程数/当前线程数/活跃线程数/最大线程数/
 * 队列任务数/队列剩余容量/已完成任务数，队列总容量 = 队列任务数 + 队列剩余容量。
 *
 * 可通过 threadpool.reporter.enabled=false 关闭。
 */
@RestController
@ConditionalOnProperty(prefix = "threadpool.reporter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ThreadPoolMetricsReporter {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    private volatile String instanceIp;
    private volatile boolean ipResolved = false;

    /**
     * 数据接口契约：GET /api/threadpool/metrics
     * 返回 200 + {"code":0,"message":"ok","data":{appName, instanceIp, timestamp, threadPools:[...]}}
     */
    @GetMapping("/api/threadpool/metrics")
    public MetricsResponse metrics() {
        long timestamp = System.currentTimeMillis();

        Map<String, ThreadPoolTaskExecutor> executors =
                applicationContext.getBeansOfType(ThreadPoolTaskExecutor.class);
        List<MetricsResponse.PoolMetrics> pools = new ArrayList<>(executors.size());

        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            ThreadPoolTaskExecutor executor = entry.getValue();
            ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();

            int queueSize = threadPoolExecutor.getQueue().size();
            int remainingCapacity = threadPoolExecutor.getQueue().remainingCapacity();
            int queueCapacity = queueSize + remainingCapacity;

            MetricsResponse.PoolMetrics pool = new MetricsResponse.PoolMetrics();
            pool.setPoolName(entry.getKey());
            pool.setCorePoolSize(executor.getCorePoolSize());
            pool.setPoolSize(threadPoolExecutor.getPoolSize());
            pool.setActiveCount(threadPoolExecutor.getActiveCount());
            pool.setMaxPoolSize(executor.getMaxPoolSize());
            pool.setQueueSize(queueSize);
            pool.setQueueRemainingCapacity(remainingCapacity);
            pool.setQueueCapacity(queueCapacity);
            pool.setCompletedTaskCount(threadPoolExecutor.getCompletedTaskCount());
            pools.add(pool);
        }
        pools.sort((a, b) -> a.getPoolName().compareTo(b.getPoolName()));

        MetricsResponse.Data data = new MetricsResponse.Data();
        data.setAppName(resolveAppName());
        data.setInstanceIp(resolveInstanceIp());
        // 机器逻辑核数：监控中心据此做线程池容量分析（CPU 密集 ≈ N，IO 密集 ≈ 2N~4N）
        data.setCpuCores(Runtime.getRuntime().availableProcessors());
        data.setTimestamp(timestamp);
        data.setThreadPools(pools);
        return MetricsResponse.ok(data);
    }

    private String resolveAppName() {
        String name = environment.getProperty("demo.app-name");
        if (name == null || name.isEmpty()) {
            name = environment.getProperty("spring.application.name");
        }
        return name == null || name.isEmpty() ? "unknown" : name;
    }

    private String resolveInstanceIp() {
        if (!ipResolved) {
            synchronized (this) {
                if (!ipResolved) {
                    try {
                        instanceIp = InetAddress.getLocalHost().getHostAddress();
                    } catch (Exception e) {
                        instanceIp = "unknown";
                    }
                    ipResolved = true;
                }
            }
        }
        return instanceIp;
    }
}
