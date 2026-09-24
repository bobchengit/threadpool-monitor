package io.itbob.threadpool.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 拉取调度线程池：所有数据源的定时拉取任务共用，
 * setRemoveOnCancelPolicy 保证取消的任务被及时清理
 */
@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler pullScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("tpm-pull-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
