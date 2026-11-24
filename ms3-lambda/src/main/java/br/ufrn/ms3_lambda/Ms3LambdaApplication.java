package br.ufrn.ms3_lambda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@SpringBootApplication
public class Ms3LambdaApplication {

    private static final Logger log = LoggerFactory.getLogger(Ms3LambdaApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(Ms3LambdaApplication.class, args);
    }

    // Bean Serverless: recebe ValidacaoRequest e retorna ValidacaoResponse
    @Bean
    public Function<ValidacaoRequest, ValidacaoResponse> validarPedido() {
        return request -> {
            log.info("MS3 Lambda validarPedido recebido - clienteId={}, pedidoId={}",
                    request.clienteId(), request.pedidoId());

            boolean valido = request.pedidoId() != null
                    && request.pedidoId().toUpperCase().startsWith("PED-")
                    && request.clienteId() != null
                    && !request.clienteId().isBlank();

            String sufixo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String protocolo = (valido ? "LAMBDA-OK-" : "LAMBDA-REJ-") + sufixo;
            String prioridade = valido ? "PRIORIDADE_NORMAL" : "REVISAO_MANUAL";

            if (valido) {
                log.info("MS3 Lambda validacao APROVADA - protocolo={}, prioridade={}, pedidoId={}",
                        protocolo, prioridade, request.pedidoId());
            } else {
                log.warn("MS3 Lambda validacao REPROVADA - protocolo={}, prioridade={}, clienteId={}, pedidoId={}",
                        protocolo, prioridade, request.clienteId(), request.pedidoId());
            }

            return new ValidacaoResponse(
                    protocolo,
                    valido,
                    prioridade,
                    Instant.now().toString()
            );
        };
    }

    public record ValidacaoRequest(String clienteId, String pedidoId) {}

    public record ValidacaoResponse(
            String protocolo,
            boolean pedidoValido,
            String prioridade,
            String processadoEm
    ) {}
}