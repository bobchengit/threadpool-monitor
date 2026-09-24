package io.itbob.threadpool.monitor.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 第三方数据源返回的线程池指标契约（监控中心拉取目标的解析模型）。
 * 契约定义：
 * GET {baseUrl}{metricsPath} -> 200 + {"code":0,"message":"ok","data":{...}}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricsContract {

    private int code;
    private String message;
    private Data data;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {

        /** 应用标识，一般取 spring.application.name */
        private String appName;
        /** 实例 IP，同一应用多实例部署时区分来源 */
        private String instanceIp;
        /** 机器 CPU 逻辑核数（Reporter 用 availableProcessors() 上报；旧版本第三方未上报时为 null） */
        private Integer cpuCores;
        /** 第三方采集时刻的毫秒时间戳（缺失时监控中心回退用本机接收时间） */
        private Long timestamp;
        private List<PoolMetric> threadPools;

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

        public List<PoolMetric> getThreadPools() {
            return threadPools;
        }

        public void setThreadPools(List<PoolMetric> threadPools) {
            this.threadPools = threadPools;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoolMetric {

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
