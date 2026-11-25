package br.ufrn.api_gateway.config;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Configura o cliente HTTP do API Gateway para evitar vazamento de conexoes no
 * pool quando o filtro CircuitBreaker lanca CircuitBreakerStatusCodeException
 * em respostas 5xx antes do fechamento automatico do ClientHttpResponse.
 */
@Configuration
public class GatewayHttpClientConfig {

    @Bean
    RestClientCustomizer close5xxResponsesCustomizer() {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            if (!response.getStatusCode().is5xxServerError()) {
                return response;
            }

            try (ClientHttpResponse original = response) {
                HttpStatusCode statusCode = original.getStatusCode();
                String statusText = original.getStatusText();
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(original.getHeaders());
                byte[] bodyBytes = original.getBody().readAllBytes();

                return new BufferedErrorClientHttpResponse(statusCode, statusText, headers, bodyBytes);
            }
        });
    }

    private static final class BufferedErrorClientHttpResponse implements ClientHttpResponse {

        private final HttpStatusCode statusCode;
        private final String statusText;
        private final HttpHeaders headers;
        private final byte[] bodyBytes;

        private BufferedErrorClientHttpResponse(
                HttpStatusCode statusCode,
                String statusText,
                HttpHeaders headers,
                byte[] bodyBytes) {
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.headers = headers;
            this.bodyBytes = bodyBytes;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return statusCode;
        }

        @Override
        public String getStatusText() throws IOException {
            return statusText;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(bodyBytes);
        }

        @Override
        public void close() {
            // Conexao original ja foi fechada antes do retorno ao chamador
        }
    }
}

