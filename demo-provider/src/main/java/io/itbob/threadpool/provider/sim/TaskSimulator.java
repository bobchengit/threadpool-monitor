package io.itbob.threadpool.provider.sim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * 任务模拟器：每 500ms 一拍，向各线程池提交 N 个随机耗时任务，
 * N = base + amplitude·sin(2πt/period + phase) + jitter，产生可见的正弦负载波动。
 * burst 模式：开启瞬间向 alarmExecutor 灌入超队列容量 1.5 倍的任务制造堆积，
 * 持续期间每拍追加提交，用于验证监控端的队列告警（>80% 标红）。
 */
@Component
public class TaskSimulator {

    private static final Logger log = LoggerFactory.getLogger(TaskSimulator.class);
    private static final long TICK_MILLIS = 500;
    private static final double TWO_PI = 2 * Math.PI;

    private final ThreadPoolTaskExecutor orderExecutor;
    private final ThreadPoolTaskExecutor reportExecutor;
    private final ThreadPoolTaskExecutor alarmExecutor;

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-simulator");
        t.setDaemon(true);
        return t;
    });

    /** burst 可通过 REST 开关 POST /api/demo/burst/{on|off} 或配置 demo.burst 切换 */
    private volatile boolean burst;

    private long tick;

    public TaskSimulator(ThreadPoolTaskExecutor orderExecutor,
                         ThreadPoolTaskExecutor reportExecutor,
                         ThreadPoolTaskExecutor alarmExecutor,
                         @Value("${demo.burst:false}") boolean burstInit) {
        this.orderExecutor = orderExecutor;
        this.reportExecutor = reportExecutor;
        this.alarmExecutor = alarmExecutor;
        this.burst = burstInit;
    }

    @PostConstruct
    public void start() {
        ticker.scheduleAtFixedRate(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
        if (burst) {
            dumpBurstTasks();
        }
        log.info("任务模拟器已启动，burst 模式: {}", burst ? "ON" : "OFF");
    }

    @PreDestroy
    public void stop() {
        ticker.shutdownNow();
    }

    public boolean isBurst() {
        return burst;
    }

    public void setBurst(boolean burst) {
        boolean changed = this.burst != burst;
        this.burst = burst;
        if (burst && changed) {
            dumpBurstTasks();
        }
        log.info("burst 模式切换为 {}", burst ? "ON" : "OFF");
    }

    private void tick() {
        tick++;
        double elapsedSec = tick * (TICK_MILLIS / 1000.0);

        // orderExecutor: core 4，任务 300~800ms，约 7 任务/秒 满负载，波形 60s
        int orderN = clampToInt(3 + 2 * Math.sin(TWO_PI * elapsedSec / 60) + jitter(1), 0, 8);
        submitTasks(orderExecutor, orderN, 300, 800);

        // reportExecutor: core 2，任务 200~600ms，波形 45s 相位错开
        int reportN = clampToInt(1.5 + 1.2 * Math.sin(TWO_PI * elapsedSec / 45 + Math.PI / 3) + jitter(1), 0, 5);
        submitTasks(reportExecutor, reportN, 200, 600);

        // alarmExecutor: core 2，任务 100~300ms，低负载运行，留给 burst 制造堆积
        int alarmN = clampToInt(1.5 + Math.sin(TWO_PI * elapsedSec / 30 + Math.PI / 2), 0, 4);
        submitTasks(alarmExecutor, alarmN, 100, 300);

        if (burst) {
            submitTasks(alarmExecutor, 10, 400, 900);
            submitTasks(reportExecutor, 3, 300, 700);
        }
    }

    private void submitTasks(ThreadPoolTaskExecutor executor, int count, int sleepMinMs, int sleepMaxMs) {
        for (int i = 0; i < count; i++) {
            try {
                executor.execute(() -> {
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextLong(sleepMinMs, sleepMaxMs + 1));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (RejectedExecutionException ignored) {
                // 队列满被拒绝是模拟负载的一部分，忽略即可
            }
        }
    }

    private void dumpBurstTasks() {
        // alarmExecutor 队列容量 50，灌入 75 个较长任务 → 立刻超过 80% 告警线
        submitTasks(alarmExecutor, 75, 400, 900);
        log.info("burst 开启：已向 alarmExecutor 灌入 75 个任务");
    }

    private static int jitter(int max) {
        return max <= 0 ? 0 : ThreadLocalRandom.current().nextInt(max + 1);
    }

    private static int clampToInt(double v, int min, int max) {
        return (int) Math.max(min, Math.min(max, Math.round(v)));
    }

    /**
     * 供状态接口查看各池当前情况
     */
    public Map<String, Object> status() {
        Map<String, Object> pools = new LinkedHashMap<>();
        addPool(pools, "orderExecutor", orderExecutor);
        addPool(pools, "reportExecutor", reportExecutor);
        addPool(pools, "alarmExecutor", alarmExecutor);
        return pools;
    }

    private void addPool(Map<String, Object> out, String name, ThreadPoolTaskExecutor executor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
        m.put("queueCapacity", executor.getThreadPoolExecutor().getQueue().size()
                + executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        m.put("activeCount", executor.getThreadPoolExecutor().getActiveCount());
        m.put("poolSize", executor.getThreadPoolExecutor().getPoolSize());
        out.put(name, m);
    }
}
