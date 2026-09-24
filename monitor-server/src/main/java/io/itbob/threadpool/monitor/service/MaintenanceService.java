package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 数据维护：快照数据统计 + 手动清理。
 * 作为每日定时保留清理（RetentionCleaner）的补充：支持按数据源粒度、灵活保留天数即时清理。
 */
@Service
public class MaintenanceService {

    private static final int BATCH_SIZE = 20000;
    private static final int MAX_KEEP_DAYS = 365;

    private final MetricSnapshotDao metricSnapshotDao;

    public MaintenanceService(MetricSnapshotDao metricSnapshotDao) {
        this.metricSnapshotDao = metricSnapshotDao;
    }

    /** 快照数据统计：按数据源的条数与时间跨度（datasourceId 为空统计全部），供清理前预览数据量 */
    public List<Map<String, Object>> stats(Long datasourceId) {
        return metricSnapshotDao.statsByDatasource(datasourceId);
    }

    /**
     * 手动清理快照：keepDays=0 表示全部清空，否则删除 keepDays 天前的数据；datasourceId 为空作用于全部数据源。
     * 分批删除避免长事务锁表，返回实际删除行数。仅清理历史快照，不影响数据源配置与调度。
     */
    public long cleanup(Long datasourceId, Integer keepDays) {
        if (keepDays == null || keepDays < 0 || keepDays > MAX_KEEP_DAYS) {
            throw new IllegalArgumentException("keepDays 取值范围 0（全部清空）~ " + MAX_KEEP_DAYS);
        }
        Timestamp cutoff = new Timestamp(System.currentTimeMillis() - keepDays * 24L * 60 * 60 * 1000);
        long total = 0;
        int deleted;
        while ((deleted = metricSnapshotDao.deleteScopeBeforeBatch(datasourceId, cutoff, BATCH_SIZE)) > 0) {
            total += deleted;
        }
        return total;
    }
}
