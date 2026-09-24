package io.itbob.threadpool.provider.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 可选的 Basic 鉴权过滤器（demo.auth.enabled=true 时生效）。
 * 用于联调监控中心的 BASIC 鉴权配置，以及验证密钥错误返回 401 的失败路径。
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "demo.auth", name = "enabled", havingValue = "true")
public class AuthFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public AuthFilter(org.springframework.core.env.Environment env) {
        String username = env.getProperty("demo.auth.username", "demo");
        String password = env.getProperty("demo.auth.password", "demo123");
        String plain = username + ":" + password;
        this.expectedToken = "Basic "
                + Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (expectedToken.equals(auth)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"demo-provider\"");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"unauthorized\",\"data\":null}");
    }
}
