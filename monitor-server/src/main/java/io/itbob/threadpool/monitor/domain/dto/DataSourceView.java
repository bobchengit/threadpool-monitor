package io.itbob.threadpool.monitor.domain.dto;

/**
 * 数据源视图（面向前端）：密钥不回传明文，仅返回是否已设置
 */
public class DataSourceView {

    private Long id;
    private String name;
    private String baseUrl;
    private String metricsPath;
    private String authType;
    private String authUsername;
    private String authHeaderName;
    private boolean authSecretSet;
    private Integer pullIntervalSec;
    private Integer queueAlertThreshold;
    private Boolean enabled;
    private String status;
    private Long lastSuccessTime;
    private String lastErrorMsg;
    private String remark;
    private Long createdAt;
    private Long updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public boolean isAuthSecretSet() {
        return authSecretSet;
    }

    public void setAuthSecretSet(boolean authSecretSet) {
        this.authSecretSet = authSecretSet;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLastSuccessTime() {
        return lastSuccessTime;
    }

    public void setLastSuccessTime(Long lastSuccessTime) {
        this.lastSuccessTime = lastSuccessTime;
    }

    public String getLastErrorMsg() {
        return lastErrorMsg;
    }

    public void setLastErrorMsg(String lastErrorMsg) {
        this.lastErrorMsg = lastErrorMsg;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
