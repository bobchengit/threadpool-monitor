package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.dao.DataSourceDao;
import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.domain.MetricSnapshot;
import io.itbob.threadpool.monitor.domain.dto.MetricsContract;
import io.itbob.threadpool.monitor.domain.dto.PullResult;
import io.itbob.threadpool.monitor.http.ProviderHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 指标拉取主链路：按数据源配置发起请求 → 解析契约 → 批量落库 → 更新状态
 */
@Service
public class MetricsPullService {

    private static final Logger log = LoggerFactory.getLogger(MetricsPullService.class);

    private final DataSourceDao dataSourceDao;
    private final MetricSnapshotDao metricSnapshotDao;
    private final ProviderHttpClient providerHttpClient;

    public MetricsPullService(DataSourceDao dataSourceDao, MetricSnapshotDao metricSnapshotDao,
                              ProviderHttpClient providerHttpClient) {
        this.dataSourceDao = dataSourceDao;
        this.metricSnapshotDao = metricSnapshotDao;
        this.providerHttpClient = providerHttpClient;
    }

    /**
     * 拉取指定数据源一次，返回结果
     */
    public PullResult pull(Long datasourceId) {
        DataSourceConfig cfg = dataSourceDao.findById(datasourceId);
        if (cfg == null) {
            return PullResult.fail("数据源不存在: id=" + datasourceId);
        }
        PullResult result = providerHttpClient.fetch(cfg);
        if (!result.isOk()) {
            dataSourceDao.updateStatus(datasourceId, "FAIL", null, result.getErrorMsg());
            return result;
        }
        MetricsContract.Data data = result.getContract().getData();
        int rowCount = data.getThreadPools() == null ? 0 : data.getThreadPools().size();
        if (rowCount > 0) {
            Timestamp metricTime = data.getTimestamp() == null
                    ? new Timestamp(System.currentTimeMillis())
                    : new Timestamp(data.getTimestamp());
            List<MetricSnapshot> snapshots = new ArrayList<>(data.getThreadPools().size());
            for (MetricsContract.PoolMetric pool : data.getThreadPools()) {
                MetricSnapshot s = new MetricSnapshot();
                s.setDatasourceId(datasourceId);
                s.setAppName(data.getAppName());
                s.setInstanceIp(data.getInstanceIp());
                s.setPoolName(pool.getPoolName());
                s.setMetricTime(metricTime);
                s.setCpuCores(data.getCpuCores());
                s.setCorePoolSize(pool.getCorePoolSize());
                s.setPoolSize(pool.getPoolSize());
                s.setActiveCount(pool.getActiveCount());
                s.setMaxPoolSize(pool.getMaxPoolSize());
                s.setQueueSize(pool.getQueueSize());
                s.setQueueRemaining(pool.getQueueRemainingCapacity());
                s.setQueueCapacity(pool.getQueueCapacity());
                s.setCompletedTaskCount(pool.getCompletedTaskCount());
                snapshots.add(s);
            }
            metricSnapshotDao.batchInsert(snapshots);
            result.setRowCount(rowCount);
        }
        dataSourceDao.updateStatus(datasourceId, "OK",
                new Timestamp(new Date().getTime()), null);
        return result;
    }

    /**
     * 调度任务调用的入口：保证任何异常都被吞掉，避免中断后续 fixed-rate
     */
    public void pullSafely(Long datasourceId) {
        try {
            pull(datasourceId);
        } catch (Exception e) {
            log.warn("拉取数据源 [{}] 时发生未捕获异常", datasourceId, e);
        }
    }
}
