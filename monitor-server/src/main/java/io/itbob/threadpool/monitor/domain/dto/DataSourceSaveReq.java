package io.itbob.threadpool.monitor.domain.dto;

/**
 * 数据源新增/更新请求。
 * 更新时 authSecret 留空/null 表示保留原密钥不变更。
 */
public class DataSourceSaveReq {

    private String name;
    private String baseUrl;
    private String metricsPath;
    private String authType;
    private String authUsername;
    private String authSecret;
    private String authHeaderName;
    private Integer pullIntervalSec;
    private Integer queueAlertThreshold;
    private Boolean enabled;
    private String remark;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getMetricsPath() {
        return metricsPath;
    }

    public void setMetricsPath(String metricsPath) {
        this.metricsPath = metricsPath;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthUsername() {
        return authUsername;
    }

    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public void setAuthSecret(String authSecret) {
        this.authSecret = authSecret;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public Integer getPullIntervalSec() {
        return pullIntervalSec;
    }

    public void setPullIntervalSec(Integer pullIntervalSec) {
        this.pullIntervalSec = pullIntervalSec;
    }

    public Integer getQueueAlertThreshold() {
        return queueAlertThreshold;
    }

    public void setQueueAlertThreshold(Integer queueAlertThreshold) {
        this.queueAlertThreshold = queueAlertThreshold;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
