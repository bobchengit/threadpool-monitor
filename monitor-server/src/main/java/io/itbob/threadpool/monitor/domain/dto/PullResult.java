package io.itbob.threadpool.monitor.domain.dto;

/**
 * 拉取/连通性测试结果
 */
public class PullResult {

    private boolean ok;
    private Integer httpStatus;
    private long latencyMs;
    /** 本次实际落库行数（手动拉取时使用） */
    private int rowCount;
    /** 测试时发现的线程池数量 */
    private int poolCount;
    private String errorMsg;
    /** 拉取成功时的契约数据（内部使用，不直接返回给前端展示页） */
    private MetricsContract contract;

    public static PullResult fail(String errorMsg) {
        PullResult r = new PullResult();
        r.ok = false;
        r.errorMsg = errorMsg;
        return r;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public int getPoolCount() {
        return poolCount;
    }

    public void setPoolCount(int poolCount) {
        this.poolCount = poolCount;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public MetricsContract getContract() {
        return contract;
    }

    public void setContract(MetricsContract contract) {
        this.contract = contract;
    }
}
