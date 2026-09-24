package io.itbob.threadpool.monitor.dao;

import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * 数据源配置表 DAO
 */
@Repository
public class DataSourceDao {

    private static final String COLS =
            "ID, NAME, BASE_URL, METRICS_PATH, AUTH_TYPE, AUTH_USERNAME, AUTH_SECRET, AUTH_HEADER_NAME, "
                    + "PULL_INTERVAL_SEC, QUEUE_ALERT_THRESHOLD, ENABLED, STATUS, LAST_SUCCESS_TIME, "
                    + "LAST_ERROR_MSG, REMARK, CREATED_AT, UPDATED_AT";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DataSourceConfig> mapper = (rs, rowNum) -> {
        DataSourceConfig c = new DataSourceConfig();
        c.setId(rs.getLong("ID"));
        c.setName(rs.getString("NAME"));
        c.setBaseUrl(rs.getString("BASE_URL"));
        c.setMetricsPath(rs.getString("METRICS_PATH"));
        c.setAuthType(rs.getString("AUTH_TYPE"));
        c.setAuthUsername(rs.getString("AUTH_USERNAME"));
        c.setAuthSecret(rs.getString("AUTH_SECRET"));
        c.setAuthHeaderName(rs.getString("AUTH_HEADER_NAME"));
        c.setPullIntervalSec(rs.getInt("PULL_INTERVAL_SEC"));
        c.setQueueAlertThreshold(rs.getInt("QUEUE_ALERT_THRESHOLD"));
        c.setEnabled(rs.getBoolean("ENABLED"));
        c.setStatus(rs.getString("STATUS"));
        c.setLastSuccessTime(rs.getTimestamp("LAST_SUCCESS_TIME"));
        c.setLastErrorMsg(rs.getString("LAST_ERROR_MSG"));
        c.setRemark(rs.getString("REMARK"));
        c.setCreatedAt(rs.getTimestamp("CREATED_AT"));
        c.setUpdatedAt(rs.getTimestamp("UPDATED_AT"));
        return c;
    };

    public DataSourceDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DataSourceConfig> findAll() {
        return jdbcTemplate.query("SELECT " + COLS + " FROM tpm_data_source ORDER BY ID", mapper);
    }

    public List<DataSourceConfig> findEnabled() {
        return jdbcTemplate.query("SELECT " + COLS + " FROM tpm_data_source WHERE ENABLED = TRUE ORDER BY ID", mapper);
    }

    public DataSourceConfig findById(Long id) {
        List<DataSourceConfig> list = jdbcTemplate.query(
                "SELECT " + COLS + " FROM tpm_data_source WHERE ID = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tpm_data_source WHERE NAME = ?", Integer.class, name);
        return count != null && count > 0;
    }

    public long insert(DataSourceConfig c) {
        final String sql = "INSERT INTO tpm_data_source (NAME, BASE_URL, METRICS_PATH, AUTH_TYPE, AUTH_USERNAME, "
                + "AUTH_SECRET, AUTH_HEADER_NAME, PULL_INTERVAL_SEC, QUEUE_ALERT_THRESHOLD, ENABLED, STATUS, REMARK) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNKNOWN', ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            // 显式指定返回 ID 列，避免 H2 将所有默认值列都作为生成键返回
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"ID"});
            int i = 1;
            ps.setString(i++, c.getName());
            ps.setString(i++, c.getBaseUrl());
            ps.setString(i++, c.getMetricsPath());
            ps.setString(i++, c.getAuthType());
            setNullableString(ps, i++, c.getAuthUsername());
            setNullableString(ps, i++, c.getAuthSecret());
            setNullableString(ps, i++, c.getAuthHeaderName());
            ps.setInt(i++, c.getPullIntervalSec());
            ps.setInt(i++, c.getQueueAlertThreshold());
            ps.setBoolean(i++, Boolean.TRUE.equals(c.getEnabled()));
            setNullableString(ps, i++, c.getRemark());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public void update(DataSourceConfig c) {
        jdbcTemplate.update("UPDATE tpm_data_source SET NAME = ?, BASE_URL = ?, METRICS_PATH = ?, AUTH_TYPE = ?, "
                        + "AUTH_USERNAME = ?, AUTH_SECRET = ?, AUTH_HEADER_NAME = ?, PULL_INTERVAL_SEC = ?, "
                        + "QUEUE_ALERT_THRESHOLD = ?, ENABLED = ?, REMARK = ?, UPDATED_AT = CURRENT_TIMESTAMP "
                        + "WHERE ID = ?",
                c.getName(), c.getBaseUrl(), c.getMetricsPath(), c.getAuthType(),
                c.getAuthUsername(), c.getAuthSecret(), c.getAuthHeaderName(),
                c.getPullIntervalSec(), c.getQueueAlertThreshold(),
                Boolean.TRUE.equals(c.getEnabled()), c.getRemark(), c.getId());
    }

    public void updateStatus(Long id, String status, Timestamp lastSuccessTime, String lastErrorMsg) {
        jdbcTemplate.update("UPDATE tpm_data_source SET STATUS = ?, LAST_SUCCESS_TIME = ?, LAST_ERROR_MSG = ?, "
                        + "UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?",
                status, lastSuccessTime, lastErrorMsg, id);
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM tpm_data_source WHERE ID = ?", id);
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
