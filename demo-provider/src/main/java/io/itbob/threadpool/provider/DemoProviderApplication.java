package io.itbob.threadpool.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模拟第三方数据源：内含 3 个线程池 + 任务模拟器 + 符合契约的指标接口。
 * 真实第三方系统只需拷贝 reporter 包下的 ThreadPoolMetricsReporter 即可接入。
 */
@SpringBootApplication
public class DemoProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoProviderApplication.class, args);
    }
}
