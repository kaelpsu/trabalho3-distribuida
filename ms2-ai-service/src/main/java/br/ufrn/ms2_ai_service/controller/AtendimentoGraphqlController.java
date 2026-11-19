package br.ufrn.ms2_ai_service.controller;

import br.ufrn.ms2_ai_service.service.AssistenteIaService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class AtendimentoGraphqlController {

    private final AssistenteIaService assistenteIaService;

    public AtendimentoGraphqlController(AssistenteIaService assistenteIaService) {
        this.assistenteIaService = assistenteIaService;
    }

    @QueryMapping
    @RateLimiter(name = "ms2AiServerRateLimiter")
    public RespostaAtendimento processarAtendimento(
            @Argument String clienteId,
            @Argument String pedidoId,
            @Argument String pergunta,
            @Argument String protocolo) {

        return assistenteIaService.gerarRespostaAtendimento(clienteId, pedidoId, pergunta, protocolo);
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