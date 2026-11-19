package br.ufrn.ms1_orquestrator.controller;

import br.ufrn.ms1_orquestrator.dto.AtendimentoRequest;
import br.ufrn.ms1_orquestrator.dto.AtendimentoResponse;
import br.ufrn.ms1_orquestrator.dto.RespostaAtendimentoMs2;
import br.ufrn.ms1_orquestrator.service.AtendimentoOrquestradorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistente")
@RefreshScope // Permite atualizar propriedades do Config Server sem reiniciar o MS1
public class AtendimentoController {

    private final AtendimentoOrquestradorService orquestradorService;

    // Valor injetado diretamente do arquivo config-repo/ms1-orquestrador.yml via Config Server
    @Value("${smartshop.mensagem-boas-vindas:Mensagem Padrao Local}")
    private String mensagemBoasVindas;

    public AtendimentoController(AtendimentoOrquestradorService orquestradorService) {
        this.orquestradorService = orquestradorService;
    }

    // Endpoint simples para testar rapidamente a atualizacao dinamica do Config Server
    @GetMapping("/info")
    public String obterInfoConfiguracao() {
        return mensagemBoasVindas;
    }

    // Endpoint principal que coordena a chamada GraphQL para o Microservico 2
    @PostMapping("/atendimento")
    public AtendimentoResponse realizarAtendimento(@RequestBody AtendimentoRequest request) {
        // invoca ms3
        var resultadoValidacao = orquestradorService.acionarLambdaValidacao(request);

        if (!resultadoValidacao.pedidoValido()) {
            return new AtendimentoResponse(
                    mensagemBoasVindas,
                    resultadoValidacao.protocolo(),
                    false,
                    resultadoValidacao.prioridade(),
                    request.clienteId(),
                    request.pedidoId(),
                    String.format("Pedido '%s' invalido para atendimento automatico. Encaminhado para %s.",
                            request.pedidoId(), resultadoValidacao.prioridade()),
                    "N/A (VALIDACAO_REPROVADA_MS3)",
                    "N/A",
                    "PEDIDO_REJEITADO_NA_VALIDACAO_LAMBDA"
            );
        }

        //invoca ms2
        RespostaAtendimentoMs2 respostaMs2 = orquestradorService.consultarAssistenteIa(request, resultadoValidacao);

        String origem = "FALLBACK_MODO_OFFLINE".equals(respostaMs2.modeloUtilizado())
                ? "RESPOSTA_DE_FALLBACK_RESILIENCE4J"
                : "COMUNICACAO_GRAPHQL_SUCESSO";

        return new AtendimentoResponse(
                mensagemBoasVindas,
                respostaMs2.protocolo(),
                resultadoValidacao.pedidoValido(),
                resultadoValidacao.prioridade(),
                respostaMs2.clienteId(),
                respostaMs2.pedidoId(),
                respostaMs2.respostaIa(),
                respostaMs2.modeloUtilizado(),
                respostaMs2.apiKeyMascarada(),
                origem
        );
    }
}