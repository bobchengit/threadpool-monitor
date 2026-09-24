package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.dao.DataSourceDao;
import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.domain.MetricSnapshot;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标查询：大屏总览（各数据源 + 各线程池最新快照）与历史区间查询
 */
@Service
public class MetricsQueryService {

    /** 历史查询最大时间跨度 24h，防止误操作全表拉取 */
    private static final long MAX_RANGE_MS = 24 * 60 * 60 * 1000L;
    private static final long DEFAULT_RANGE_MS = 30 * 60 * 1000L;

    private final DataSourceDao dataSourceDao;
    private final MetricSnapshotDao metricSnapshotDao;

    public MetricsQueryService(DataSourceDao dataSourceDao, MetricSnapshotDao metricSnapshotDao) {
        this.dataSourceDao = dataSourceDao;
        this.metricSnapshotDao = metricSnapshotDao;
    }

    /**
     * 总览：每个数据源 + 每个线程池的最新快照，含队列使用率与告警标志
     */
    public Map<String, Object> overview(Long datasourceId) {
        List<DataSourceConfig> sources = datasourceId == null
                ? dataSourceDao.findAll()
                : java.util.Collections.singletonList(dataSourceDao.findById(datasourceId));

        // key: datasourceId|poolName -> 最新快照
        // 逐源查询（每源走索引），替代旧的全表 GROUP BY 版本，源数量少、总代价低
        Map<String, MetricSnapshot> latestMap = new HashMap<>();
        for (DataSourceConfig cfg : sources) {
            if (cfg == null) {
                continue;
            }
            for (MetricSnapshot s : metricSnapshotDao.findLatestPerPool(cfg.getId())) {
                latestMap.put(s.getDatasourceId() + "|" + s.getPoolName(), s);
            }
        }

        List<Map<String, Object>> sourceList = new ArrayList<>(sources.size());
        int alertPoolTotal = 0;
        for (DataSourceConfig cfg : sources) {
            if (cfg == null) {
                continue;
            }
            Map<String, Object> sourceMap = new LinkedHashMap<>();
            sourceMap.put("id", cfg.getId());
            sourceMap.put("name", cfg.getName());
            sourceMap.put("status", cfg.getStatus());
            sourceMap.put("enabled", cfg.getEnabled());
            sourceMap.put("pullIntervalSec", cfg.getPullIntervalSec());
            sourceMap.put("queueAlertThreshold", cfg.getQueueAlertThreshold());
            sourceMap.put("lastSuccessTime",
                    cfg.getLastSuccessTime() == null ? null : cfg.getLastSuccessTime().getTime());
            sourceMap.put("lastErrorMsg", cfg.getLastErrorMsg());

            List<Map<String, Object>> pools = new ArrayList<>();
            for (MetricSnapshot s : latestMap.values()) {
                if (!s.getDatasourceId().equals(cfg.getId())) {
                    continue;
                }
                int capacity = Math.max(s.getQueueCapacity(), 0);
                double usage = capacity == 0 ? 0 : s.getQueueSize() * 100.0 / capacity;
                boolean alert = usage >= cfg.getQueueAlertThreshold();
                if (alert) {
                    alertPoolTotal++;
                }
                Map<String, Object> pool = new LinkedHashMap<>();
                pool.put("poolName", s.getPoolName());
                pool.put("appName", s.getAppName());
                pool.put("instanceIp", s.getInstanceIp());
                pool.put("metricTime", s.getMetricTime() == null ? null : s.getMetricTime().getTime());
                pool.put("corePoolSize", s.getCorePoolSize());
                pool.put("poolSize", s.getPoolSize());
                pool.put("activeCount", s.getActiveCount());
                pool.put("maxPoolSize", s.getMaxPoolSize());
                pool.put("queueSize", s.getQueueSize());
                pool.put("queueRemaining", s.getQueueRemaining());
                pool.put("queueCapacity", s.getQueueCapacity());
                pool.put("completedTaskCount", s.getCompletedTaskCount());
                pool.put("queueUsagePercent", Math.round(usage * 10) / 10.0);
                pool.put("alert", alert);
                pools.add(pool);
            }
            // 池名排序保证卡片顺序稳定
            pools.sort((a, b) -> String.valueOf(a.get("poolName")).compareTo(String.valueOf(b.get("poolName"))));
            sourceMap.put("pools", pools);
            sourceMap.put("alertPoolCount", pools.stream().filter(p -> Boolean.TRUE.equals(p.get("alert"))).count());
            sourceList.add(sourceMap);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("serverTime", System.currentTimeMillis());
        resp.put("datasources", sourceList);
        resp.put("alertPoolTotal", alertPoolTotal);
        return resp;
    }

    /**
     * 历史区间查询（毫秒时间戳），时间升序
     */
    public List<Map<String, Object>> history(Long datasourceId, String poolName, Long start, Long end) {
        if (datasourceId == null) {
            throw new IllegalArgumentException("datasourceId 不能为空");
        }
        if (poolName == null || poolName.trim().isEmpty()) {
            throw new IllegalArgumentException("poolName 不能为空");
        }
        long endMs = end == null ? System.currentTimeMillis() : end;
        long startMs = start == null ? endMs - DEFAULT_RANGE_MS : start;
        if (endMs <= startMs) {
            throw new IllegalArgumentException("end 必须大于 start");
        }
        if (endMs - startMs > MAX_RANGE_MS) {
            throw new IllegalArgumentException("时间跨度不能超过 24 小时");
        }
        List<MetricSnapshot> snapshots = metricSnapshotDao.findHistory(
                datasourceId, poolName.trim(), new Timestamp(startMs), new Timestamp(endMs));
        List<Map<String, Object>> rows = new ArrayList<>(snapshots.size());
        for (MetricSnapshot s : snapshots) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", s.getMetricTime() == null ? null : s.getMetricTime().getTime());
            row.put("corePoolSize", s.getCorePoolSize());
            row.put("poolSize", s.getPoolSize());
            row.put("activeCount", s.getActiveCount());
            row.put("maxPoolSize", s.getMaxPoolSize());
            row.put("queueSize", s.getQueueSize());
            row.put("queueRemaining", s.getQueueRemaining());
            row.put("queueCapacity", s.getQueueCapacity());
            row.put("completedTaskCount", s.getCompletedTaskCount());
            rows.add(row);
        }
        return rows;
    }
}
