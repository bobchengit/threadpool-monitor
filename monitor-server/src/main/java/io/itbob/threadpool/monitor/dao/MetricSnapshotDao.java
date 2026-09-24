package io.itbob.threadpool.monitor.dao;

import io.itbob.threadpool.monitor.domain.MetricSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标快照表 DAO：高频批量写入 + 区间查询 + 清理
 */
@Repository
public class MetricSnapshotDao {

    private static final String COLS =
            "ID, DATASOURCE_ID, APP_NAME, INSTANCE_IP, POOL_NAME, METRIC_TIME, CPU_CORES, CORE_POOL_SIZE, POOL_SIZE, "
                    + "ACTIVE_COUNT, MAX_POOL_SIZE, QUEUE_SIZE, QUEUE_REMAINING, QUEUE_CAPACITY, COMPLETED_TASK_COUNT";

    private static final String ALIASED_COLS =
            "s.ID, s.DATASOURCE_ID, s.APP_NAME, s.INSTANCE_IP, s.POOL_NAME, s.METRIC_TIME, s.CPU_CORES, s.CORE_POOL_SIZE, "
                    + "s.POOL_SIZE, s.ACTIVE_COUNT, s.MAX_POOL_SIZE, s.QUEUE_SIZE, s.QUEUE_REMAINING, "
                    + "s.QUEUE_CAPACITY, s.COMPLETED_TASK_COUNT";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MetricSnapshot> mapper = (rs, rowNum) -> {
        MetricSnapshot s = new MetricSnapshot();
        s.setId(rs.getLong("ID"));
        s.setDatasourceId(rs.getLong("DATASOURCE_ID"));
        s.setAppName(rs.getString("APP_NAME"));
        s.setInstanceIp(rs.getString("INSTANCE_IP"));
        s.setPoolName(rs.getString("POOL_NAME"));
        s.setMetricTime(rs.getTimestamp("METRIC_TIME"));
        int cpuCores = rs.getInt("CPU_CORES");
        s.setCpuCores(rs.wasNull() ? null : cpuCores);
        s.setCorePoolSize(rs.getInt("CORE_POOL_SIZE"));
        s.setPoolSize(rs.getInt("POOL_SIZE"));
        s.setActiveCount(rs.getInt("ACTIVE_COUNT"));
        s.setMaxPoolSize(rs.getInt("MAX_POOL_SIZE"));
        s.setQueueSize(rs.getInt("QUEUE_SIZE"));
        s.setQueueRemaining(rs.getInt("QUEUE_REMAINING"));
        s.setQueueCapacity(rs.getInt("QUEUE_CAPACITY"));
        s.setCompletedTaskCount(rs.getLong("COMPLETED_TASK_COUNT"));
        return s;
    };

    public MetricSnapshotDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量写入一次拉取的快照
     */
    public void batchInsert(List<MetricSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO tpm_metric_snapshot (DATASOURCE_ID, APP_NAME, INSTANCE_IP, POOL_NAME, METRIC_TIME, "
                        + "CPU_CORES, CORE_POOL_SIZE, POOL_SIZE, ACTIVE_COUNT, MAX_POOL_SIZE, QUEUE_SIZE, "
                        + "QUEUE_REMAINING, QUEUE_CAPACITY, COMPLETED_TASK_COUNT) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                snapshots, snapshots.size(), (PreparedStatement ps, MetricSnapshot s) -> {
                    int i = 1;
                    ps.setLong(i++, s.getDatasourceId());
                    ps.setString(i++, s.getAppName());
                    ps.setString(i++, s.getInstanceIp());
                    ps.setString(i++, s.getPoolName());
                    ps.setTimestamp(i++, s.getMetricTime());
                    if (s.getCpuCores() == null) { ps.setNull(i++, java.sql.Types.INTEGER); }
                    else { ps.setInt(i++, s.getCpuCores()); }
                    ps.setInt(i++, s.getCorePoolSize());
                    ps.setInt(i++, s.getPoolSize());
                    ps.setInt(i++, s.getActiveCount());
                    ps.setInt(i++, s.getMaxPoolSize());
                    ps.setInt(i++, s.getQueueSize());
                    ps.setInt(i++, s.getQueueRemaining());
                    ps.setInt(i++, s.getQueueCapacity());
                    ps.setLong(i++, s.getCompletedTaskCount());
                });
    }

    /**
     * 查询某个数据源某个线程池的历史快照（时间升序），供折线图使用
     */
    public List<MetricSnapshot> findHistory(Long datasourceId, String poolName, Timestamp start, Timestamp end) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM tpm_metric_snapshot "
                        + "WHERE DATASOURCE_ID = ? AND POOL_NAME = ? AND METRIC_TIME >= ? AND METRIC_TIME <= ? "
                        + "ORDER BY METRIC_TIME",
                mapper, datasourceId, poolName, start, end);
    }

    /**
     * 查询某个数据源内每个线程池的最新一条快照，供大屏总览使用。
     * 子查询内先按 DATASOURCE_ID 过滤再聚合，走 IDX_SNAP_QUERY 前缀索引，
     * 避免快照表增长后的全表 GROUP BY（分析/总览均高频调用）。
     */
    public List<MetricSnapshot> findLatestPerPool(Long datasourceId) {
        String sql = "SELECT " + ALIASED_COLS + " FROM tpm_metric_snapshot s JOIN ("
                + "SELECT POOL_NAME, MAX(METRIC_TIME) AS MT FROM tpm_metric_snapshot "
                + "WHERE DATASOURCE_ID = ? GROUP BY POOL_NAME) t "
                + "ON s.DATASOURCE_ID = ? AND s.POOL_NAME = t.POOL_NAME AND s.METRIC_TIME = t.MT";
        return jdbcTemplate.query(sql, mapper, datasourceId, datasourceId);
    }

    /**
     * 查询某个数据源在时间窗口内的全部快照（时间升序）。
     * 供容量分析使用：只走 DATASOURCE_ID 前缀索引 + 窗口过滤，避免全表 GROUP BY。
     */
    public List<MetricSnapshot> findWindow(Long datasourceId, Timestamp start, Timestamp end) {
        return jdbcTemplate.query(
                "SELECT " + COLS + " FROM tpm_metric_snapshot "
                        + "WHERE DATASOURCE_ID = ? AND METRIC_TIME >= ? AND METRIC_TIME <= ? "
                        + "ORDER BY METRIC_TIME",
                mapper, datasourceId, start, end);
    }

    public void deleteByDatasourceId(Long datasourceId) {
        jdbcTemplate.update("DELETE FROM tpm_metric_snapshot WHERE DATASOURCE_ID = ?", datasourceId);
    }

    /**
     * 分批删除早于 cutoff 的快照（全部数据源），返回本次实际删除行数（0 表示已清理完毕）。
     * 使用 FETCH FIRST 分批，避免长事务锁表。定时保留清理（RetentionCleaner）使用。
     */
    public int deleteBeforeBatch(Timestamp cutoff, int batchSize) {
        return jdbcTemplate.update(
                "DELETE FROM tpm_metric_snapshot WHERE ID IN "
                        + "(SELECT ID FROM tpm_metric_snapshot WHERE METRIC_TIME < ? FETCH FIRST ? ROWS ONLY)",
                cutoff, batchSize);
    }

    /**
     * 按数据源聚合快照统计：条数 + 最早/最晚时间（datasourceId 为空则统计全部数据源），供手动清理前预览数据量
     */
    public List<Map<String, Object>> statsByDatasource(Long datasourceId) {
        boolean scoped = datasourceId != null;
        String sql = "SELECT DATASOURCE_ID, COUNT(*) AS CNT, MIN(METRIC_TIME) AS MIN_T, MAX(METRIC_TIME) AS MAX_T "
                + "FROM tpm_metric_snapshot" + (scoped ? " WHERE DATASOURCE_ID = ?" : "") + " GROUP BY DATASOURCE_ID";
        return jdbcTemplate.query(sql, (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("datasourceId", rs.getLong("DATASOURCE_ID"));
            m.put("snapshotCount", rs.getLong("CNT"));
            m.put("earliestTime", rs.getTimestamp("MIN_T") == null ? null : rs.getTimestamp("MIN_T").getTime());
            m.put("latestTime", rs.getTimestamp("MAX_T") == null ? null : rs.getTimestamp("MAX_T").getTime());
            return m;
        }, scoped ? new Object[]{datasourceId} : new Object[]{});
    }

    /**
     * 分批删除指定范围（数据源可选，空 = 全部）内早于 cutoff 的快照，返回本次删除行数（0 表示清理完毕）。
     * 与 deleteBeforeBatch 相同的 FETCH FIRST 分批策略，避免长事务锁表。
     */
    public int deleteScopeBeforeBatch(Long datasourceId, Timestamp cutoff, int batchSize) {
        boolean scoped = datasourceId != null;
        String sql = "DELETE FROM tpm_metric_snapshot WHERE ID IN (SELECT ID FROM tpm_metric_snapshot "
                + "WHERE METRIC_TIME < ?" + (scoped ? " AND DATASOURCE_ID = ?" : "") + " FETCH FIRST ? ROWS ONLY)";
        Object[] args = scoped ? new Object[]{cutoff, datasourceId, batchSize} : new Object[]{cutoff, batchSize};
        return jdbcTemplate.update(sql, args);
    }
}
