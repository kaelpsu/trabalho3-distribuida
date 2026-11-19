package br.ufrn.ms1_orquestrator.service;

import br.ufrn.ms1_orquestrator.client.LambdaValidacaoClient;
import br.ufrn.ms1_orquestrator.dto.AtendimentoRequest;
import br.ufrn.ms1_orquestrator.dto.RespostaAtendimentoMs2;
import br.ufrn.ms1_orquestrator.dto.ValidacaoLambdaRequest;
import br.ufrn.ms1_orquestrator.dto.ValidacaoLambdaResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AtendimentoOrquestradorService {

    private static final Logger log = LoggerFactory.getLogger(AtendimentoOrquestradorService.class);
    private final HttpSyncGraphQlClient graphQlClient;
    private final LambdaValidacaoClient lambdaValidacaoClient;

    public AtendimentoOrquestradorService(HttpSyncGraphQlClient graphQlClient, LambdaValidacaoClient lambdaValidacaoClient) {
        this.graphQlClient = graphQlClient;
        this.lambdaValidacaoClient = lambdaValidacaoClient;
    }

    @CircuitBreaker(name = "ms3LambdaCircuitBreaker")
    @Retry(name = "ms3LambdaRetry", fallbackMethod = "fallbackValidacaoLambda")
    public ValidacaoLambdaResponse acionarLambdaValidacao(AtendimentoRequest request) {
        log.info("Acionando MS3 (Lambda Serverless) via HTTP Interface para o pedido: {}", request.pedidoId());
        return lambdaValidacaoClient.validarPedido(
            new ValidacaoLambdaRequest(request.clienteId(), request.pedidoId())
        );
    }

    public ValidacaoLambdaResponse fallbackValidacaoLambda(AtendimentoRequest request, Throwable erro) {
        log.warn("FALLBACK ACIONADO no MS1 ao chamar MS3 (Lambda)! Motivo: {}", erro.getMessage());

        String sufixo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return new ValidacaoLambdaResponse(
                "LAMBDA-FALLBACK-" + sufixo,
                true,
                "CONTINGENCIA_LAMBDA_OFFLINE",
                Instant.now().toString()
        );
    }

    @CircuitBreaker(name = "ms2AiCircuitBreaker")
    @Retry(name = "ms2AiRetry", fallbackMethod = "fallbackAtendimentoMs2")
    @RateLimiter(name = "ms2AiClientRateLimiter")
    @Bulkhead(name = "ms2AiBulkhead")
    public RespostaAtendimentoMs2 consultarAssistenteIa(AtendimentoRequest request, ValidacaoLambdaResponse validacao) {
        log.info("Enviando consulta GraphQL para o ms2-ai-service. Pedido: {}, Protocolo: {}",
                request.pedidoId(), validacao.protocolo());

        String queryGraphQl = """
                query($clienteId: String!, $pedidoId: String!, $pergunta: String!, $protocolo: String!) {
                    processarAtendimento(
                        clienteId: $clienteId,
                        pedidoId: $pedidoId,
                        pergunta: $pergunta,
                        protocolo: $protocolo
                    ) {
                        protocolo
                        clienteId
                        pedidoId
                        respostaIa
                        modeloUtilizado
                        apiKeyMascarada
                    }
                }
                """;

        return graphQlClient.document(queryGraphQl)
                .variable("clienteId", request.clienteId())
                .variable("pedidoId", request.pedidoId())
                .variable("pergunta", request.pergunta())
                .variable("protocolo", validacao.protocolo())
                .retrieveSync("processarAtendimento")
                .toEntity(RespostaAtendimentoMs2.class);
    }

    public RespostaAtendimentoMs2 fallbackAtendimentoMs2(
            AtendimentoRequest request, ValidacaoLambdaResponse validacao, Throwable erro) {

        log.warn("FALLBACK ACIONADO no MS1! Motivo da falha ao chamar MS2: {}", erro.getMessage());

        String protocolo = validacao.protocolo();
        String mensagemContingencia = String.format(
                "[CONTINGENCIA / FALLBACK] Atendimento registrado sob protocolo %s. " +
                "Nosso assistente de IA esta temporariamente indisponivel ou sobrecarregado (%s), " +
                "mas o seu pedido %s ja esta na fila prioritaria.",
                protocolo, erro.getClass().getSimpleName(), request.pedidoId()
        );

        return new RespostaAtendimentoMs2(
                protocolo,
                request.clienteId(),
                request.pedidoId(),
                mensagemContingencia,
                "FALLBACK_MODO_OFFLINE",
                "N/A (VAULT_INDISPONIVEL_EM_FALLBACK)"
        );
    }
}