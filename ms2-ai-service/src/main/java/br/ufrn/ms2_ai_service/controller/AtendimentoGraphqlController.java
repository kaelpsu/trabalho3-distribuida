package br.ufrn.ms2_ai_service.controller;

import br.ufrn.ms2_ai_service.service.AssistenteIaService;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
@RefreshScope
public class AtendimentoGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(AtendimentoGraphqlController.class);
    private final AssistenteIaService assistenteIaService;

    @Value("${smartshop.resiliencia.ativa:true}")
    private boolean resilienciaAtiva;

    public AtendimentoGraphqlController(AssistenteIaService assistenteIaService) {
        this.assistenteIaService = assistenteIaService;
    }

    @QueryMapping
    public RespostaAtendimento processarAtendimento(
            @Argument String clienteId,
            @Argument String pedidoId,
            @Argument String pergunta,
            @Argument String protocolo) {

        log.info("GraphQL processarAtendimento recebido - clienteId={}, pedidoId={}, protocolo={}, resilienciaAtiva={}",
                clienteId, pedidoId, protocolo, resilienciaAtiva);

        return resilienciaAtiva
                ? assistenteIaService.gerarRespostaAtendimentoComRateLimiter(clienteId, pedidoId, pergunta, protocolo)
                : assistenteIaService.gerarRespostaAtendimento(clienteId, pedidoId, pergunta, protocolo);
    }

    @GraphQlExceptionHandler(RequestNotPermitted.class)
    public GraphQLError handleRateLimiterException(RequestNotPermitted ex) {
        log.warn("Rate Limiter Server-Side (ms2AiServerRateLimiter) bloqueou requisicao no ms2-ai-service: {}",
                ex.getMessage());
        return GraphqlErrorBuilder.newError()
                .errorType(ErrorType.ExecutionAborted)
                .message("TOO_MANY_REQUESTS: Limite de requisicoes (ms2AiServerRateLimiter) atingido no ms2-ai-service.")
                .build();
    }

    public record RespostaAtendimento(
            String protocolo,
            String clienteId,
            String pedidoId,
            String respostaIa,
            String modeloUtilizado,
            String apiKeyMascarada
    ) {}
}