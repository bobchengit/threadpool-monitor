package io.itbob.threadpool.monitor.domain;

import java.sql.Timestamp;

/**
 * 线程池指标快照（一行 = 某数据源某线程池某次拉取的指标）
 */
public class MetricSnapshot {

    private Long id;
    private Long datasourceId;
    private String appName;
    private String instanceIp;
    private String poolName;
    private Timestamp metricTime;
    /** 机器 CPU 逻辑核数（第三方 Reporter 上报，旧版本可能为 null） */
    private Integer cpuCores;
    private int corePoolSize;
    private int poolSize;
    private int activeCount;
    private int maxPoolSize;
    private int queueSize;
    private int queueRemaining;
    private int queueCapacity;
    private long completedTaskCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getInstanceIp() {
        return instanceIp;
    }

    public void setInstanceIp(String instanceIp) {
        this.instanceIp = instanceIp;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public Timestamp getMetricTime() {
        return metricTime;
    }

    public void setMetricTime(Timestamp metricTime) {
        this.metricTime = metricTime;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQueueRemaining() {
        return queueRemaining;
    }

    public void setQueueRemaining(int queueRemaining) {
        this.queueRemaining = queueRemaining;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(long completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }
}
