package io.itbob.threadpool.monitor.controller;

import io.itbob.threadpool.monitor.common.ApiResponse;
import io.itbob.threadpool.monitor.domain.dto.DataSourceSaveReq;
import io.itbob.threadpool.monitor.domain.dto.DataSourceView;
import io.itbob.threadpool.monitor.domain.dto.PullResult;
import io.itbob.threadpool.monitor.service.DataSourceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理接口
 */
@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    public ApiResponse list() {
        List<DataSourceView> list = dataSourceService.list();
        return ApiResponse.ok(list);
    }

    @PostMapping
    public ApiResponse create(@RequestBody DataSourceSaveReq req) {
        return ApiResponse.ok(dataSourceService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse update(@PathVariable Long id, @RequestBody DataSourceSaveReq req) {
        return ApiResponse.ok(dataSourceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResponse.ok();
    }

    /** 连通性测试 */
    @PostMapping("/{id}/test")
    public ApiResponse test(@PathVariable Long id) {
        PullResult result = dataSourceService.test(id);
        result.setContract(null); // 契约数据不返回给前端
        return ApiResponse.ok(result);
    }

    /** 手动立即拉取一次 */
    @PostMapping("/{id}/pull")
    public ApiResponse pullNow(@PathVariable Long id) {
        PullResult result = dataSourceService.pullNow(id);
        result.setContract(null);
        return ApiResponse.ok(result);
    }
}
