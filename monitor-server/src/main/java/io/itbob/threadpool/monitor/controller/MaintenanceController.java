package io.itbob.threadpool.monitor.controller;

import io.itbob.threadpool.monitor.common.ApiResponse;
import io.itbob.threadpool.monitor.service.MaintenanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据维护接口：快照统计 + 手动清理（数据源管理页使用）
 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /** 快照数据统计：datasourceId 为空统计全部数据源 */
    @GetMapping("/stats")
    public ApiResponse stats(@RequestParam(required = false) Long datasourceId) {
        return ApiResponse.ok(maintenanceService.stats(datasourceId));
    }

    /** 手动清理快照：keepDays=0 全部清空，否则删除 N 天前数据；datasourceId 为空作用于全部数据源 */
    @PostMapping("/cleanup")
    public ApiResponse cleanup(@RequestParam(required = false) Long datasourceId,
                               @RequestParam Integer keepDays) {
        long deleted = maintenanceService.cleanup(datasourceId, keepDays);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("deleted", deleted);
        return ApiResponse.ok(r);
    }
}
