package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.crypto.AesGcmEncryptor;
import io.itbob.threadpool.monitor.dao.DataSourceDao;
import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.domain.dto.DataSourceSaveReq;
import io.itbob.threadpool.monitor.domain.dto.DataSourceView;
import io.itbob.threadpool.monitor.domain.dto.PullResult;
import io.itbob.threadpool.monitor.http.ProviderHttpClient;
import io.itbob.threadpool.monitor.scheduler.DynamicPullScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源管理：CRUD + 变更后动态重调度 + 连通性测试
 */
@Service
public class DataSourceService {

    private static final int MIN_INTERVAL_SEC = 1;

    private final DataSourceDao dataSourceDao;
    private final MetricSnapshotDao metricSnapshotDao;
    private final ProviderHttpClient providerHttpClient;
    private final MetricsPullService metricsPullService;
    private final DynamicPullScheduler pullScheduler;
    private final AesGcmEncryptor encryptor;

    public DataSourceService(DataSourceDao dataSourceDao, MetricSnapshotDao metricSnapshotDao,
                             ProviderHttpClient providerHttpClient, MetricsPullService metricsPullService,
                             DynamicPullScheduler pullScheduler, AesGcmEncryptor encryptor) {
        this.dataSourceDao = dataSourceDao;
        this.metricSnapshotDao = metricSnapshotDao;
        this.providerHttpClient = providerHttpClient;
        this.metricsPullService = metricsPullService;
        this.pullScheduler = pullScheduler;
        this.encryptor = encryptor;
    }

    public List<DataSourceView> list() {
        return dataSourceDao.findAll().stream().map(this::toView).collect(Collectors.toList());
    }

    public DataSourceView create(DataSourceSaveReq req) {
        validate(req, true);
        DataSourceConfig cfg = new DataSourceConfig();
        applyReq(cfg, req, null);
        cfg.setStatus("UNKNOWN");
        long id = dataSourceDao.insert(cfg);
        cfg.setId(id);
        pullScheduler.reschedule(cfg);
        return toView(dataSourceDao.findById(id));
    }

    public DataSourceView update(Long id, DataSourceSaveReq req) {
        validate(req, false);
        DataSourceConfig cfg = requireById(id);
        applyReq(cfg, req, cfg.getAuthSecret());
        dataSourceDao.update(cfg);
        pullScheduler.reschedule(dataSourceDao.findById(id));
        return toView(dataSourceDao.findById(id));
    }

    /**
     * 删除数据源并级联清理其全部快照，同时取消调度
     */
    @Transactional
    public void delete(Long id) {
        requireById(id);
        pullScheduler.cancel(id);
        metricSnapshotDao.deleteByDatasourceId(id);
        dataSourceDao.deleteById(id);
    }

    /**
     * 连通性测试：真实请求一次目标接口
     */
    public PullResult test(Long id) {
        return providerHttpClient.fetch(requireById(id));
    }

    /**
     * 手动立即拉取一次
     */
    public PullResult pullNow(Long id) {
        requireById(id);
        return metricsPullService.pull(id);
    }

    private void applyReq(DataSourceConfig cfg, DataSourceSaveReq req, String oldSecret) {
        cfg.setName(req.getName().trim());
        cfg.setBaseUrl(req.getBaseUrl().trim());
        cfg.setMetricsPath(req.getMetricsPath() == null || req.getMetricsPath().trim().isEmpty()
                ? "/api/threadpool/metrics" : req.getMetricsPath().trim());
        String authType = req.getAuthType() == null || req.getAuthType().trim().isEmpty()
                ? "NONE" : req.getAuthType().trim().toUpperCase();
        cfg.setAuthType(authType);
        cfg.setAuthUsername("BASIC".equals(authType) ? trimToNull(req.getAuthUsername()) : null);
        cfg.setAuthHeaderName("HEADER".equals(authType) ? trimToNull(req.getAuthHeaderName()) : null);
        // 密钥：更新时留空表示保留原值；有新值则加密落库；NONE 清空
        if (!"NONE".equals(authType)) {
            if (req.getAuthSecret() != null && !req.getAuthSecret().isEmpty()) {
                cfg.setAuthSecret(encryptor.encrypt(req.getAuthSecret()));
            } else {
                cfg.setAuthSecret(oldSecret);
            }
        } else {
            cfg.setAuthSecret(null);
        }
        cfg.setPullIntervalSec(req.getPullIntervalSec() == null ? 30 : req.getPullIntervalSec());
        cfg.setQueueAlertThreshold(req.getQueueAlertThreshold() == null ? 80 : req.getQueueAlertThreshold());
        cfg.setEnabled(req.getEnabled() == null || req.getEnabled());
        cfg.setRemark(trimToNull(req.getRemark()));
    }

    private void validate(DataSourceSaveReq req, boolean isCreate) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (isCreate && dataSourceDao.existsByName(req.getName().trim())) {
            throw new IllegalArgumentException("数据源名称已存在: " + req.getName().trim());
        }
        if (req.getBaseUrl() == null || req.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("数据源地址不能为空");
        }
        String authType = req.getAuthType() == null || req.getAuthType().trim().isEmpty()
                ? "NONE" : req.getAuthType().trim().toUpperCase();
        boolean secretBlank = req.getAuthSecret() == null || req.getAuthSecret().isEmpty();
        switch (authType) {
            case "BASIC":
                if (req.getAuthUsername() == null || req.getAuthUsername().trim().isEmpty()) {
                    throw new IllegalArgumentException("Basic 认证必须填写用户名");
                }
                if (secretBlank && isCreate) {
                    throw new IllegalArgumentException("Basic 认证必须填写密码");
                }
                break;
            case "BEARER":
            case "HEADER":
                if (secretBlank && isCreate) {
                    throw new IllegalArgumentException("该鉴权方式必须填写密钥");
                }
                if ("HEADER".equals(authType)
                        && (req.getAuthHeaderName() == null || req.getAuthHeaderName().trim().isEmpty())) {
                    throw new IllegalArgumentException("自定义 Header 认证必须填写 Header 名称");
                }
                break;
            case "NONE":
                break;
            default:
                throw new IllegalArgumentException("不支持的鉴权方式: " + authType);
        }
        if (req.getPullIntervalSec() != null && req.getPullIntervalSec() < MIN_INTERVAL_SEC) {
            throw new IllegalArgumentException("刷新频率不能低于 " + MIN_INTERVAL_SEC + " 秒");
        }
        if (req.getQueueAlertThreshold() != null
                && (req.getQueueAlertThreshold() < 1 || req.getQueueAlertThreshold() > 100)) {
            throw new IllegalArgumentException("队列告警阈值必须在 1~100 之间");
        }
    }

    private DataSourceConfig requireById(Long id) {
        DataSourceConfig cfg = dataSourceDao.findById(id);
        if (cfg == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + id);
        }
        return cfg;
    }

    private DataSourceView toView(DataSourceConfig cfg) {
        DataSourceView v = new DataSourceView();
        v.setId(cfg.getId());
        v.setName(cfg.getName());
        v.setBaseUrl(cfg.getBaseUrl());
        v.setMetricsPath(cfg.getMetricsPath());
        v.setAuthType(cfg.getAuthType());
        v.setAuthUsername(cfg.getAuthUsername());
        v.setAuthHeaderName(cfg.getAuthHeaderName());
        v.setAuthSecretSet(cfg.getAuthSecret() != null && !cfg.getAuthSecret().isEmpty());
        v.setPullIntervalSec(cfg.getPullIntervalSec());
        v.setQueueAlertThreshold(cfg.getQueueAlertThreshold());
        v.setEnabled(cfg.getEnabled());
        v.setStatus(cfg.getStatus());
        v.setLastSuccessTime(cfg.getLastSuccessTime() == null ? null : cfg.getLastSuccessTime().getTime());
        v.setLastErrorMsg(cfg.getLastErrorMsg());
        v.setRemark(cfg.getRemark());
        v.setCreatedAt(cfg.getCreatedAt() == null ? null : cfg.getCreatedAt().getTime());
        v.setUpdatedAt(cfg.getUpdatedAt() == null ? null : cfg.getUpdatedAt().getTime());
        return v;
    }

    private String trimToNull(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.trim();
    }
}
