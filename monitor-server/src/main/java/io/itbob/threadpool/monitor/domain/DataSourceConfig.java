package io.itbob.threadpool.monitor.domain;

import java.sql.Timestamp;

/**
 * 数据源配置实体（AUTH_SECRET 存储的是 AES-GCM 密文）
 */
public class DataSourceConfig {

    private Long id;
    private String name;
    private String baseUrl;
    private String metricsPath;
    /** NONE / BEARER / BASIC / HEADER */
    private String authType;
    private String authUsername;
    private String authSecret;
    private String authHeaderName;
    private Integer pullIntervalSec;
    private Integer queueAlertThreshold;
    private Boolean enabled;
    /** OK / FAIL / UNKNOWN（最近一次拉取结果） */
    private String status;
    private Timestamp lastSuccessTime;
    private String lastErrorMsg;
    private String remark;
    private Timestamp createdAt;
    private Timestamp updatedAt;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getLastSuccessTime() {
        return lastSuccessTime;
    }

    public void setLastSuccessTime(Timestamp lastSuccessTime) {
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

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
