package io.itbob.threadpool.monitor.http;

import io.itbob.threadpool.monitor.domain.DataSourceConfig;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 鉴权方式应用器：按数据源配置把鉴权信息写入请求头
 */
public enum AuthApplier {

    /** 无鉴权 */
    NONE {
        @Override
        public void apply(HttpHeaders headers, DataSourceConfig cfg) {
        }
    },

    /** Bearer Token：Authorization: Bearer {secret} */
    BEARER {
        @Override
        public void apply(HttpHeaders headers, DataSourceConfig cfg) {
            headers.setBearerAuth(cfg.getAuthSecret());
        }
    },

    /** Basic 认证：Authorization: Basic base64(username:password) */
    BASIC {
        @Override
        public void apply(HttpHeaders headers, DataSourceConfig cfg) {
            String plain = cfg.getAuthUsername() + ":" + cfg.getAuthSecret();
            String encoded = Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encoded);
        }
    },

    /** 自定义 Header：{headerName}: {secret} */
    HEADER {
        @Override
        public void apply(HttpHeaders headers, DataSourceConfig cfg) {
            headers.set(cfg.getAuthHeaderName(), cfg.getAuthSecret());
        }
    };

    public abstract void apply(HttpHeaders headers, DataSourceConfig cfg);

    public static AuthApplier forType(String authType) {
        if (authType == null || authType.isEmpty()) {
            return NONE;
        }
        return valueOf(authType.trim().toUpperCase());
    }
}
