package io.itbob.threadpool.monitor.scheduler;

import io.itbob.threadpool.monitor.dao.DataSourceDao;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.service.MetricsPullService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态拉取调度器：
 * 每个数据源对应一个 ScheduledFuture，新增/修改/删除/启停后调用 reschedule/cancel 即时生效，无需重启。
 * fixedRate 语义保证同一数据源的拉取不会并发重叠（上一轮未结束时下一轮顺延）。
 */
@Component
public class DynamicPullScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicPullScheduler.class);
    private static final int MIN_INTERVAL_SEC = 1;

    private final Map<Long, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    private final ThreadPoolTaskScheduler taskScheduler;
    private final MetricsPullService metricsPullService;
    private final DataSourceDao dataSourceDao;

    public DynamicPullScheduler(ThreadPoolTaskScheduler taskScheduler,
                                MetricsPullService metricsPullService,
                                DataSourceDao dataSourceDao) {
        this.taskScheduler = taskScheduler;
        this.metricsPullService = metricsPullService;
        this.dataSourceDao = dataSourceDao;
    }

    /**
     * 应用启动完成后恢复所有 enabled 数据源的调度
     */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreOnStartup() {
        int count = 0;
        for (DataSourceConfig cfg : dataSourceDao.findEnabled()) {
            reschedule(cfg);
            count++;
        }
        log.info("拉取调度初始化完成：已恢复 {} 个数据源的定时拉取", count);
    }

    /**
     * 按最新配置重新调度（取消旧任务 → enabled 时按新频率注册）
     */
    public synchronized void reschedule(DataSourceConfig cfg) {
        cancel(cfg.getId());
        if (Boolean.TRUE.equals(cfg.getEnabled())) {
            long intervalSec = Math.max(MIN_INTERVAL_SEC, cfg.getPullIntervalSec() == null ? 30 : cfg.getPullIntervalSec());
            ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                    () -> metricsPullService.pullSafely(cfg.getId()),
                    Duration.ofSeconds(intervalSec));
            tasks.put(cfg.getId(), future);
            log.info("数据源 [{}] 已注册定时拉取，间隔 {} 秒", cfg.getName(), intervalSec);
        } else {
            log.info("数据源 [{}] 已停用，不注册拉取任务", cfg.getName());
        }
    }

    /**
     * 取消调度（不打断正在执行的拉取）
     */
    public synchronized void cancel(Long id) {
        ScheduledFuture<?> future = tasks.remove(id);
        if (future != null) {
            future.cancel(false);
        }
    }
}
