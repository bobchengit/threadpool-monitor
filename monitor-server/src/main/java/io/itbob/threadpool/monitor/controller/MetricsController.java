package io.itbob.threadpool.monitor.controller;

import io.itbob.threadpool.monitor.common.ApiResponse;
import io.itbob.threadpool.monitor.service.MetricsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标查询接口
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsQueryService metricsQueryService;
    private final io.itbob.threadpool.monitor.service.CapacityAnalysisService capacityAnalysisService;

    public MetricsController(MetricsQueryService metricsQueryService,
                             io.itbob.threadpool.monitor.service.CapacityAnalysisService capacityAnalysisService) {
        this.metricsQueryService = metricsQueryService;
        this.capacityAnalysisService = capacityAnalysisService;
    }

    /** 大屏总览：各数据源状态 + 各线程池最新快照 */
    @GetMapping("/overview")
    public ApiResponse overview(@RequestParam(required = false) Long datasourceId) {
        return ApiResponse.ok(metricsQueryService.overview(datasourceId));
    }

    /** 历史区间查询：datasourceId + poolName + [start, end]（毫秒时间戳） */
    @GetMapping("/history")
    public ApiResponse history(@RequestParam Long datasourceId,
                               @RequestParam String poolName,
                               @RequestParam(required = false) Long start,
                               @RequestParam(required = false) Long end) {
        return ApiResponse.ok(metricsQueryService.history(datasourceId, poolName, start, end));
    }

    /** 线程池容量分析：窗口内利用率/队列/吞吐证据 + 线程数建议（windowMinutes 默认 30，范围 5~1440） */
    @GetMapping("/analysis")
    public ApiResponse analysis(@RequestParam Long datasourceId,
                                @RequestParam(required = false, defaultValue = "30") Integer windowMinutes) {
        return ApiResponse.ok(capacityAnalysisService.analyze(datasourceId, windowMinutes));
    }
}
