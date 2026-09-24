package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * 历史数据保留清理：每日 03:30 按保留天数分批删除过期快照
 */
@Component
public class RetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleaner.class);
    private static final int BATCH_SIZE = 20000;

    private final MetricSnapshotDao metricSnapshotDao;

    @Value("${monitor.retention-days:30}")
    private int retentionDays;

    public RetentionCleaner(MetricSnapshotDao metricSnapshotDao) {
        this.metricSnapshotDao = metricSnapshotDao;
    }

    @Scheduled(cron = "${monitor.retention-cron:0 30 3 * * ?}")
    public void clean() {
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000);
        long total = 0;
        int deleted;
        while ((deleted = metricSnapshotDao.deleteBeforeBatch(cutoff, BATCH_SIZE)) > 0) {
            total += deleted;
        }
        if (total > 0) {
            log.info("历史快照清理完成：删除 {} 天前数据 {} 行（截止 {}）", retentionDays, total, cutoff);
        } else {
            log.debug("历史快照清理完成：无过期数据（保留 {} 天）", retentionDays);
        }
    }
}
