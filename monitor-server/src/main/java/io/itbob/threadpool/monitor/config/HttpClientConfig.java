package io.itbob.threadpool.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 拉取第三方数据源用的 HttpClient：
 * 连接超时 3s / 读取超时 5s，保证单个数据源故障不会长期占用调度线程
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate providerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }
}
