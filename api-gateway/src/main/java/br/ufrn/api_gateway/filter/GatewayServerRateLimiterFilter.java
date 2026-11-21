package br.ufrn.api_gateway.filter;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayServerRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayServerRateLimiterFilter.class);
    private static final String GATEWAY_RATE_LIMITER = "gatewayServerRateLimiter";
    private static final String GATEWAY_CIRCUIT_BREAKER = "ms1CircuitBreaker";

    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${smartshop.resiliencia.ativa:true}")
    private boolean resilienciaAtiva;

    public GatewayServerRateLimiterFilter(
            RateLimiterRegistry rateLimiterRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/assistente");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        sincronizarCircuitBreakerGateway(resilienciaAtiva);

        if (!resilienciaAtiva) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(GATEWAY_RATE_LIMITER);
        if (!rateLimiter.acquirePermission()) {
            log.warn("Rate Limiter Server-Side ({}) bloqueou requisição no API Gateway para {}",
                    GATEWAY_RATE_LIMITER, request.getRequestURI());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            String body = """
                    {"status":429,"erro":"TOO_MANY_REQUESTS","rateLimiter":"%s","origem":"RATE_LIMITER_SERVER_SIDE_API_GATEWAY","mensagem":"Limite de requisicoes atingido na borda (API Gateway). Tente novamente em instantes.","timestamp":"%s"}"""
                    .formatted(GATEWAY_RATE_LIMITER, Instant.now().toString());
            response.getWriter().write(body);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sincronizarCircuitBreakerGateway(boolean resilienciaAtiva) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(GATEWAY_CIRCUIT_BREAKER);
        boolean isDisabled = cb.getState() == CircuitBreaker.State.DISABLED;
        if (resilienciaAtiva == isDisabled) {
            synchronized (this) {
                isDisabled = cb.getState() == CircuitBreaker.State.DISABLED;
                if (!resilienciaAtiva && !isDisabled) {
                    cb.transitionToDisabledState();
                    log.info("Feature flag smartshop.resiliencia.ativa=false: Circuit Breaker '{}' desativado no API Gateway.",
                            GATEWAY_CIRCUIT_BREAKER);
                } else if (resilienciaAtiva && isDisabled) {
                    cb.transitionToClosedState();
                    log.info("Feature flag smartshop.resiliencia.ativa=true: Circuit Breaker '{}' reativado (CLOSED) no API Gateway.",
                            GATEWAY_CIRCUIT_BREAKER);
                }
            }
        }
    }
}
