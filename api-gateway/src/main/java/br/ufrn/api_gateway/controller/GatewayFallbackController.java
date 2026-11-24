package br.ufrn.api_gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class GatewayFallbackController {

    private static final Logger log = LoggerFactory.getLogger(GatewayFallbackController.class);

    /**
     * Endpoint de fallback acionado pelo filtro CircuitBreaker do API Gateway
     * quando o ms1-orquestrador está indisponível ou retornando falhas.
     */
    @RequestMapping("/fallback/atendimento")
    public ResponseEntity<Map<String, Object>> fallbackAtendimento() {
        log.warn("FALLBACK API GATEWAY acionado (ms1CircuitBreaker) - ms1-orquestrador indisponivel ou instavel; retornando HTTP 503.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "erro", "SERVICO_INDISPONIVEL",
                        "circuitBreaker", "ms1CircuitBreaker",
                        "origem", "FALLBACK_API_GATEWAY",
                        "mensagem", "O servico ms1-orquestrador esta temporariamente indisponivel ou instavel. Circuit Breaker do API Gateway acionado.",
                        "timestamp", Instant.now().toString()
                ));
    }
}
