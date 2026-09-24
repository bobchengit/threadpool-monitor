package io.itbob.threadpool.monitor.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.itbob.threadpool.monitor.crypto.AesGcmEncryptor;
import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import io.itbob.threadpool.monitor.domain.dto.MetricsContract;
import io.itbob.threadpool.monitor.domain.dto.PullResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 第三方数据源 HTTP 客户端：拉取指标 + 连通性测试。
 * 密钥在此处解密后注入请求头，不落日志。
 */
@Component
public class ProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AesGcmEncryptor encryptor;

    public ProviderHttpClient(RestTemplate providerRestTemplate, ObjectMapper objectMapper,
                              AesGcmEncryptor encryptor) {
        this.restTemplate = providerRestTemplate;
        this.objectMapper = objectMapper;
        this.encryptor = encryptor;
    }

    /**
     * 按配置拉取一次指标契约。任何失败都以 ok=false 的 PullResult 返回，不抛异常。
     */
    public PullResult fetch(DataSourceConfig cfg) {
        String url = normalizeBaseUrl(cfg.getBaseUrl()) + cfg.getMetricsPath();
        long start = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            // 库中 AUTH_SECRET 为密文，发起请求前先解密（仅作用于本次请求的内存副本，不落库）
            cfg.setAuthSecret(encryptor.decrypt(cfg.getAuthSecret()));
            AuthApplier.forType(cfg.getAuthType()).apply(headers, cfg);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                return fail(resp.getStatusCodeValue(), latencyMs, "HTTP " + resp.getStatusCodeValue());
            }

            MetricsContract contract;
            try {
                contract = objectMapper.readValue(resp.getBody(), MetricsContract.class);
            } catch (Exception e) {
                return fail(resp.getStatusCodeValue(), latencyMs, "响应不是合法的指标契约 JSON: " + e.getMessage());
            }
            if (contract.getData() == null || contract.getData().getThreadPools() == null) {
                return fail(resp.getStatusCodeValue(), latencyMs, "响应缺少 data.threadPools 字段");
            }

            PullResult r = new PullResult();
            r.setOk(true);
            r.setHttpStatus(resp.getStatusCodeValue());
            r.setLatencyMs(latencyMs);
            r.setPoolCount(contract.getData().getThreadPools().size());
            r.setContract(contract);
            return r;
        } catch (HttpStatusCodeException e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return fail(e.getRawStatusCode(), latencyMs,
                    "HTTP " + e.getRawStatusCode() + ": " + snippet(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return fail(null, latencyMs, "连接失败: " + e.getMostSpecificCause().getMessage());
        } catch (Exception e) {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.warn("拉取数据源 [{}] 异常", cfg.getName(), e);
            return fail(null, latencyMs, e.getMessage());
        }
    }

    private PullResult fail(Integer httpStatus, long latencyMs, String errorMsg) {
        PullResult r = PullResult.fail(errorMsg);
        r.setHttpStatus(httpStatus);
        r.setLatencyMs(latencyMs);
        return r;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String snippet(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "..." : oneLine;
    }
}
