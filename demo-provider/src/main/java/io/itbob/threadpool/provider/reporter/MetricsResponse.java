package io.itbob.threadpool.provider.reporter;

import java.util.List;

/**
 * 线程池指标契约响应体（与监控中心约定的数据接口格式）
 */
public class MetricsResponse {

    private int code;
    private String message;
    private Data data;

    public static MetricsResponse ok(Data data) {
        MetricsResponse r = new MetricsResponse();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    public static class Data {

        private String appName;
        private String instanceIp;
        /** 机器 CPU 逻辑核数（容量分析用） */
        private Integer cpuCores;
        private Long timestamp;
        private List<PoolMetrics> threadPools;

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

        public Integer getCpuCores() {
            return cpuCores;
        }

        public void setCpuCores(Integer cpuCores) {
            this.cpuCores = cpuCores;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }

        public List<PoolMetrics> getThreadPools() {
            return threadPools;
        }

        public void setThreadPools(List<PoolMetrics> threadPools) {
            this.threadPools = threadPools;
        }
    }

    public static class PoolMetrics {

        private String poolName;
        private int corePoolSize;
        private int poolSize;
        private int activeCount;
        private int maxPoolSize;
        private int queueSize;
        private int queueRemainingCapacity;
        private int queueCapacity;
        private long completedTaskCount;

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
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

        public int getQueueRemainingCapacity() {
            return queueRemainingCapacity;
        }

        public void setQueueRemainingCapacity(int queueRemainingCapacity) {
            this.queueRemainingCapacity = queueRemainingCapacity;
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
}
