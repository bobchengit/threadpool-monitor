package io.itbob.threadpool.provider.controller;

import io.itbob.threadpool.provider.sim.TaskSimulator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示控制接口：burst 告警开关与状态查看
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final TaskSimulator taskSimulator;

    public DemoController(TaskSimulator taskSimulator) {
        this.taskSimulator = taskSimulator;
    }

    @PostMapping("/burst/{onOrOff}")
    public Map<String, Object> burst(@PathVariable String onOrOff) {
        boolean on = "on".equalsIgnoreCase(onOrOff);
        if (!on && !"off".equalsIgnoreCase(onOrOff)) {
            throw new IllegalArgumentException("路径仅支持 on / off");
        }
        taskSimulator.setBurst(on);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("burst", taskSimulator.isBurst());
        return resp;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("burst", taskSimulator.isBurst());
        resp.put("pools", taskSimulator.status());
        return resp;
    }
}
