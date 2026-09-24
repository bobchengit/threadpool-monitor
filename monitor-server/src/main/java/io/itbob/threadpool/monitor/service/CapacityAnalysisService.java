package io.itbob.threadpool.monitor.service;

import io.itbob.threadpool.monitor.dao.DataSourceDao;
import io.itbob.threadpool.monitor.dao.MetricSnapshotDao;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.domain.MetricSnapshot;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线程池容量分析：基于最近窗口内的快照，评估"当前线程数是否充分发挥机器能力"，
 * 并推断任务画像（CPU 密集 / IO 密集 / 混合型），给出可执行的调参建议。
 *
 * 证据链（契约只有线程池运行指标，无法直接测线程在"算"还是在"等"，故为推断 + 依据说明）：
 * 1. 线程利用率：峰值/平均活跃线程占 maxPoolSize 的比例；
 * 2. 队列证据：峰值使用率 + 前后半段队列均值趋势；
 * 3. 吞吐证据：窗口净增速 + 峰值瞬时吞吐（相邻快照 completed 增速最大值）；
 * 4. 任务画像（Little's Law）：单线程吞吐 = 吞吐 / 平均活跃线程数，反推单任务平均驻留 W：
 *    - W ≤ 200ms（单线程吞吐 ≥ 5/s）→ 短任务高周转 → CPU 密集特征；
 *    - W ≥ 500ms（单线程吞吐 < 2/s）→ 长驻留任务，再结合配置意图细分：
 *      线程数 ≤ N+1 → 重计算型长任务（CPU 密集）；线程数 ≥ 2N → 线程多在等外部资源（IO 密集）；
 *      介于两者之间 → 混合型；
 *    - 窗口内几乎无任务执行 → 无法判定（诚实降级，并给出采集演进路径）；全程空闲 → 空闲（区分"无负载"与"证据不足"）；
 * 5. 调参建议按 任务画像 × 利用率等级 组合生成，量化到具体线程数：
 *    CPU 密集目标 ≈ N+1（超过 N+1 只增加上下文切换）；IO 密集目标 ≈ 峰值到达率 λ × 驻留 W × 1.5，
 *    并提示下游并发上限（DB 连接池/限流）约束与拒绝策略检查。
 */
@Service
public class CapacityAnalysisService {

    private static final long MAX_WINDOW_MS = 24 * 60 * 60 * 1000L;

    /** 任务画像判定的最小完成任务数：样本置信度取决于任务绝对数量（Little's Law 基于总量），而非被空闲期稀释的窗口平均速率 */
    private static final int MIN_TASKS_FOR_PROFILE = 30;

    private final DataSourceDao dataSourceDao;
    private final MetricSnapshotDao metricSnapshotDao;

    public CapacityAnalysisService(DataSourceDao dataSourceDao, MetricSnapshotDao metricSnapshotDao) {
        this.dataSourceDao = dataSourceDao;
        this.metricSnapshotDao = metricSnapshotDao;
    }

    /**
     * 分析指定数据源在 windowMinutes 窗口内的线程池容量匹配情况。
     * 实现：一次窗口区间查询 + 内存按池分组（避免逐池查询与全表聚合，窗口数据量小、响应快）。
     */
    public Map<String, Object> analyze(Long datasourceId, Integer windowMinutes) {
        DataSourceConfig cfg = dataSourceDao.findById(datasourceId);
        if (cfg == null) {
            throw new IllegalArgumentException("数据源不存在: id=" + datasourceId);
        }
        int winMin = windowMinutes == null ? 30 : windowMinutes;
        if (winMin < 5 || winMin * 60_000L > MAX_WINDOW_MS) {
            throw new IllegalArgumentException("windowMinutes 取值范围 5 ~ 1440");
        }
        long endMs = System.currentTimeMillis();
        long startMs = endMs - winMin * 60_000L;

        List<MetricSnapshot> rows = metricSnapshotDao.findWindow(
                datasourceId, new Timestamp(startMs), new Timestamp(endMs));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("datasourceId", datasourceId);
        resp.put("name", cfg.getName());
        resp.put("windowMinutes", winMin);
        resp.put("reportTime", endMs);

        if (rows.isEmpty()) {
            resp.put("cpuCores", null);
            resp.put("summary", "窗口内无快照数据");
            resp.put("note", "该数据源在分析窗口内没有拉取到任何快照（可能长时间离线或刚接入），"
                    + "请确认数据源在线后重试，或扩大分析窗口。");
            resp.put("pools", new ArrayList<>());
            return resp;
        }

        // cpuCores：窗口内快照携带的机器核数（第三方 Reporter 上报）
        Integer cpuCores = rows.get(rows.size() - 1).getCpuCores();

        // 按池分组（rows 已按时间升序）
        Map<String, List<MetricSnapshot>> byPool = new LinkedHashMap<>();
        for (MetricSnapshot s : rows) {
            byPool.computeIfAbsent(s.getPoolName(), k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> pools = new ArrayList<>();
        int good = 0, ok = 0, warn = 0, crit = 0, noData = 0, idle = 0;
        for (Map.Entry<String, List<MetricSnapshot>> e : byPool.entrySet()) {
            Map<String, Object> p = analyzePool(e.getKey(), e.getValue(), cpuCores);
            switch ((String) p.get("level")) {
                case "GOOD": good++; break;
                case "OK": ok++; break;
                case "WARN": warn++; break;
                case "CRIT": crit++; break;
                case "IDLE": idle++; break;
                default: noData++;
            }
            pools.add(p);
        }
        pools.sort((a, b) -> String.valueOf(a.get("poolName")).compareTo(String.valueOf(b.get("poolName"))));

        StringBuilder summary = new StringBuilder();
        if (crit > 0) { summary.append(crit).append(" 个池线程不足，"); }
        if (warn > 0) { summary.append(warn).append(" 个池需关注，"); }
        if (idle > 0) { summary.append(idle).append(" 个池空闲，"); }
        if (good > 0) { summary.append(good).append(" 个池匹配良好，"); }
        if (ok > 0) { summary.append(ok).append(" 个池基本匹配，"); }
        if (noData > 0) { summary.append(noData).append(" 个池样本不足，"); }
        if (summary.length() > 2) { summary.setLength(summary.length() - 1); }

        resp.put("cpuCores", cpuCores);
        resp.put("summary", summary.toString());
        resp.put("note", "结论基于窗口内线程利用率与队列证据；CPU 密集任务建议线程数≈核数N，IO 密集≈2N~4N。"
                + (cpuCores == null ? "当前数据源未上报 CPU 核数（升级第三方 Reporter 后可结合核数给出参考值）。" : ""));
        resp.put("pools", pools);
        return resp;
    }

    private Map<String, Object> analyzePool(String poolName, List<MetricSnapshot> rows, Integer cpuCores) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("poolName", poolName);
        List<String> suggestions = new ArrayList<>();

        if (rows.size() < 5) {
            p.put("level", "NO_DATA");
            p.put("sampleCount", rows.size());
            p.put("conclusion", "窗口内样本不足（" + rows.size() + " 条），无法评估，请稍后重试或扩大窗口");
            return p;
        }

        MetricSnapshot first = rows.get(0);
        MetricSnapshot last = rows.get(rows.size() - 1);

        int maxPool = 0;
        long activeSum = 0, maxActive = 0;
        long queueSum = 0;
        double maxQueueUsage = 0;
        for (MetricSnapshot s : rows) {
            maxPool = Math.max(maxPool, s.getMaxPoolSize());
            activeSum += s.getActiveCount();
            maxActive = Math.max(maxActive, s.getActiveCount());
            queueSum += s.getQueueSize();
            if (s.getQueueCapacity() > 0) {
                maxQueueUsage = Math.max(maxQueueUsage, s.getQueueSize() * 100.0 / s.getQueueCapacity());
            }
        }
        double avgActive = activeSum * 1.0 / rows.size();

        // 队列堆积趋势：后半段均值 - 前半段均值（>0 表示在增长）
        int mid = rows.size() / 2;
        double front = 0, back = 0;
        for (int i = 0; i < mid; i++) { front += rows.get(i).getQueueSize(); }
        for (int i = mid; i < rows.size(); i++) { back += rows.get(i).getQueueSize(); }
        double queueTrend = (back / (rows.size() - mid)) - (front / mid);

        // 吞吐：窗口内已完成任务净增速（/s）
        // completedTaskCount 是进程级计数器，进程重启会从 0 重新累计；
        // 若检测到窗口内计数器回退（last < first），回溯到最后一次重置点之后再计算
        long completedDiff = last.getCompletedTaskCount() - first.getCompletedTaskCount();
        long elapsedMs = last.getMetricTime().getTime() - first.getMetricTime().getTime();
        // 有效完成数：计数器回退（进程重启）时取重置点之后的增量，作为"窗口内实际完成了多少任务"的依据
        long effCompleted;
        double throughput;
        if (completedDiff >= 0) {
            effCompleted = completedDiff;
            throughput = elapsedMs > 0 ? completedDiff * 1000.0 / elapsedMs : 0;
        } else {
            int resetIdx = 0;
            for (int i = rows.size() - 1; i > 0; i--) {
                if (rows.get(i).getCompletedTaskCount() < rows.get(i - 1).getCompletedTaskCount()) {
                    resetIdx = i;
                    break;
                }
            }
            if (resetIdx > 0 && rows.size() - resetIdx >= 5) {
                MetricSnapshot f2 = rows.get(resetIdx);
                long el2 = last.getMetricTime().getTime() - f2.getMetricTime().getTime();
                effCompleted = last.getCompletedTaskCount() - f2.getCompletedTaskCount();
                throughput = el2 > 0 ? effCompleted * 1000.0 / el2 : 0;
            } else {
                effCompleted = 0;
                throughput = 0;
            }
        }

        // 峰值瞬时吞吐：相邻快照 completed 增速的最大值（跳过计数器重置点）
        // 代表该池实际处理能力上限（饱和段），是 IO 密集目标线程数 λ×W 的 λ 取值依据
        double peakThroughput = 0;
        for (int i = 1; i < rows.size(); i++) {
            long c = rows.get(i).getCompletedTaskCount();
            long cPrev = rows.get(i - 1).getCompletedTaskCount();
            long dt = rows.get(i).getMetricTime().getTime() - rows.get(i - 1).getMetricTime().getTime();
            if (c >= cPrev && dt > 0) {
                peakThroughput = Math.max(peakThroughput, (c - cPrev) * 1000.0 / dt);
            }
        }

        // Little's Law 任务画像：L(平均活跃线程) = λ(吞吐) × W(平均驻留) → W = L / λ
        // 分母下限 0.05 仅防除零：低活跃钳到 1 会压低单线程吞吐，误伤稀疏/突发型负载的画像判定
        double perThreadTput = throughput / Math.max(avgActive, 0.05);
        double dwellSec = throughput > 0 ? avgActive / throughput : 0;

        double saturationMax = maxPool > 0 ? maxActive * 100.0 / maxPool : 0;   // 峰值线程利用率 %
        double avgRatio = maxPool > 0 ? avgActive * 100.0 / maxPool : 0;        // 平均线程利用率 %
        maxQueueUsage = Math.round(maxQueueUsage * 10) / 10.0;

        p.put("corePoolSize", last.getCorePoolSize());
        p.put("maxPoolSize", maxPool);
        p.put("sampleCount", rows.size());
        p.put("avgActive", round1(avgActive));
        p.put("maxActive", maxActive);
        p.put("activeRatioAvg", round1(avgRatio));
        p.put("activeRatioMax", round1(saturationMax));
        p.put("avgQueueSize", round1(queueSum * 1.0 / rows.size()));
        p.put("maxQueueUsage", maxQueueUsage);
        p.put("queueTrend", round1(queueTrend));
        p.put("throughput", round1(throughput));
        p.put("peakThroughput", round1(peakThroughput));
        p.put("perThreadTput", round1(perThreadTput));

        /* ---------- 任务画像判定（CPU密集 / IO密集 / 混合型 / 空闲 / 无法判定） ---------- */
        String profile;
        String basis;
        if (effCompleted <= 0 && maxActive == 0) {
            // 全程空闲：无任务执行属于"没有负载"而非"证据不足"，单独标注，避免空闲池整屏"无法判定"造成误导
            profile = "空闲";
            basis = "窗口内无任何任务执行（完成 0 个、峰值活跃 0），无画像证据；"
                    + "如需评估该池，扩大分析窗口（如 24 小时）覆盖其工作时段后复测";
        } else if (effCompleted <= 0) {
            profile = "无法判定";
            basis = "窗口内有线程活跃但无任务完成：任务驻留超过分析窗口（超长任务）或完成计数器重置，无法反推类型";
        } else if (effCompleted < MIN_TASKS_FOR_PROFILE) {
            profile = "无法判定";
            basis = "窗口内仅完成 " + effCompleted + " 个任务，统计置信度不足，无法从任务驻留时间反推类型";
        } else if (perThreadTput >= 5) {
            // 单任务驻留 ≤ 200ms：短任务高周转，瓶颈在 CPU 计算
            profile = "CPU密集";
            basis = "单线程吞吐 " + round1(perThreadTput) + "/s（平均驻留 ≈ " + Math.round(dwellSec * 1000)
                    + "ms），短任务高周转，线程瓶颈在 CPU 计算";
        } else if (perThreadTput < 2) {
            // 单任务驻留 ≥ 500ms：长驻留任务，结合配置意图细分
            boolean bigPool = cpuCores != null && maxPool >= cpuCores * 2;
            boolean smallPool = cpuCores != null && maxPool <= cpuCores + 1;
            if (bigPool) {
                profile = "IO密集";
                basis = "单线程吞吐 " + round1(perThreadTput) + "/s（平均驻留 ≈ " + Math.round(dwellSec * 1000)
                        + "ms）+ 线程数 " + maxPool + " ≥ 2×核数 → 长驻留加大线程池，线程多在等待外部资源（远程调用/DB/IO）";
            } else if (smallPool) {
                profile = "CPU密集";
                basis = "单线程吞吐 " + round1(perThreadTput) + "/s（平均驻留 ≈ " + Math.round(dwellSec * 1000)
                        + "ms）+ 线程数 " + maxPool + " ≤ N+1 → 长驻留任务配小线程池，疑似重计算型长任务（批处理/编解码）";
            } else {
                profile = "混合型";
                basis = "单线程吞吐 " + round1(perThreadTput) + "/s（平均驻留 ≈ " + Math.round(dwellSec * 1000)
                        + "ms），长驻留任务且线程配置介于 N+1 与 2N 之间，计算与等待并存";
            }
        } else {
            profile = "混合型";
            basis = "单线程吞吐 " + round1(perThreadTput) + "/s（平均驻留 ≈ " + Math.round(dwellSec * 1000)
                    + "ms），任务驻留中等，计算与等待并存";
        }
        // 突发型负载提示：峰值瞬时吞吐远高于窗口均值，说明任务集中在短促突发内执行，均值被空闲期稀释
        if (throughput > 0 && peakThroughput >= throughput * 5) {
            basis += "；负载呈突发模式（峰值瞬时吞吐 " + round1(peakThroughput) + "/s，窗口均值仅 "
                    + round1(throughput) + "/s）";
        }
        p.put("taskProfile", profile);
        p.put("profileBasis", basis);

        // 结论只给定性判断，数字证据已在前端指标网格中展示，不再复述
        boolean saturated = saturationMax >= 100;   // 线程是否已打满（供建议分支使用）
        String level;
        String conclusion;
        if ("空闲".equals(profile)) {
            // 空闲≠富余：无负载不算"需关注"，单独等级避免稀释概览条的告警信号
            level = "IDLE";
            conclusion = "线程池空闲：窗口内无任务执行，无容量压力";
        } else if (saturationMax >= 100) {
            if (queueTrend > 0.5 || maxQueueUsage >= 95) {
                level = "CRIT";
                conclusion = "线程不足：线程已耗尽且队列堆积" + (queueTrend > 0.5 ? "持续增长" : "居高不下");
            } else {
                level = "WARN";
                conclusion = "高峰期线程已打满上限，但队列消化正常、未持续堆积";
            }
        } else if (saturationMax < 50 && avgRatio < 20 && maxQueueUsage < 20) {
            level = "WARN";
            conclusion = "线程明显富余：峰值利用率不到一半，平均利用率极低，队列基本为空";
        } else if (saturationMax < 30) {
            level = "OK";
            conclusion = "线程偏空：负载较低，当前配置能满足需求";
        } else {
            level = "GOOD";
            conclusion = "配置与负载匹配良好：线程有峰值余量、队列水位健康";
        }
        p.put("level", level);
        p.put("conclusion", conclusion);

        /* ---------- 量化建议：任务画像 × 利用率等级（给出具体数字） ---------- */
        if (!"无法判定".equals(profile)) {
            suggestions.add("任务画像：" + profile + "。" + basis);
        } else {
            suggestions.add("任务画像无法判定：建议在任务提交处包装计时（提交时间 vs 完成时间），"
                    + "或升级 Reporter 上报任务执行耗时分布后复测");
        }

        // IO 密集/混合型的目标线程数：λ(饱和段吞吐) × W(实测驻留) × 1.5 安全系数
        double ioTarget = Math.ceil(Math.max(peakThroughput, throughput) * dwellSec * 1.5);

        if (saturated) {
            if ("CPU密集".equals(profile)) {
                if (cpuCores != null && maxPool < cpuCores + 1) {
                    suggestions.add("CPU 密集且线程已打满：maxPoolSize 提升到 " + (cpuCores + 1)
                            + "（N=" + cpuCores + "；超过 N+1 只会增加上下文切换，无吞吐收益）");
                } else {
                    suggestions.add("线程数已达 CPU 密集合理上限（"
                            + (cpuCores != null ? "N+1=" + (cpuCores + 1) : String.valueOf(maxPool)) + "）仍饱和："
                            + "瓶颈在 CPU 本身，优先降低单任务耗时（缓存/批量化/算法优化），而非继续加线程；"
                            + "必要时扩容实例或将该池部分负载拆走");
                }
            } else if ("IO密集".equals(profile)) {
                if (ioTarget > maxPool) {
                    suggestions.add("IO 密集且队列堆积：按 λ×W×1.5 估算（峰值吞吐 " + round1(Math.max(peakThroughput, throughput))
                            + "/s × 驻留 " + Math.round(dwellSec * 1000) + "ms × 1.5），建议 maxPoolSize 提升到 " + (int) ioTarget);
                } else {
                    suggestions.add("IO 密集但加线程收益有限（估算需求 ≈ " + (int) ioTarget + " ≤ 当前 " + maxPool
                            + "）：瓶颈在下游容量，检查 DB 连接池大小/远程接口限流/超时配置——线程数超过下游并发容量后只会增加排队");
                }
                suggestions.add("排查慢任务：统计该池任务的下游耗时分布（慢 SQL、超时重试），定位占用线程最久的调用");
            } else {
                suggestions.add("混合型任务饱和：建议拆分快慢任务到独立线程池（线程池隔离），避免慢任务占满线程拖垮快任务；"
                        + "慢任务池按 IO 密集扩容（估算 ≈ " + (int) ioTarget + "）");
            }
            if (maxQueueUsage >= 95) {
                suggestions.add("队列使用率已达 " + maxQueueUsage + "%：确认 RejectedExecutionHandler 行为——"
                        + "AbortPolicy 会抛异常丢任务，CallerRunsPolicy 会拖慢提交线程，扩容前先确认拒绝路径可接受");
            }
        } else if ("WARN".equals(level) && avgRatio < 20) {
            if ("CPU密集".equals(profile) && cpuCores != null) {
                suggestions.add("CPU 密集且利用率极低：maxPoolSize 可收缩至 N+1=" + (cpuCores + 1)
                        + "（corePoolSize 维持小值，线程按需创建即可）");
            } else if ("IO密集".equals(profile)) {
                int shrink = Math.max((int) Math.ceil(maxActive * 1.3), last.getCorePoolSize());
                suggestions.add("IO 密集且峰值活跃仅 " + maxActive + "：maxPoolSize 可收缩至 " + shrink
                        + "（峰值×1.3 保留突发余量），每线程约 1MB 栈，可回收约 " + (maxPool - shrink) + "MB 内存");
                suggestions.add("注意：若该池为突发流量缓冲设计（大促/批处理窗口），保留当前余量是合理的");
            } else {
                suggestions.add("线程富余：可适度收缩，建议先观察更长窗口（如 24 小时）的峰值再决定幅度");
            }
        } else if ("GOOD".equals(level)) {
            suggestions.add("当前配置与负载匹配良好，无需调整；建议将本分析纳入例行巡检，观察趋势变化");
        } else if ("IDLE".equals(level)) {
            suggestions.add("窗口内无任务执行：若长期空闲，可收缩线程数回收内存；若预期有流量，排查上游任务提交链路");
        } else {
            suggestions.add("负载偏低或波动，维持现状即可，随业务量增长复评");
        }
        p.put("suggestions", suggestions);
        return p;
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
