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
@RefreshScope // permite atualizar propriedades do Config Server sem reiniciar o MS1
public class AtendimentoController {

    private final AtendimentoOrquestradorService orquestradorService;

    // valor injetado diretamente do arquivo config-repo/ms1-orquestrador.yml via Config Server
    @Value("${smartshop.mensagem-boas-vindas:Mensagem Padrao Local}")
    private String mensagemBoasVindas;

    // feature flag global via config-repo/application.yml para desativar resiliencia em testes de carga
    @Value("${smartshop.resiliencia.ativa:true}")
    private boolean resilienciaAtiva;

    public AtendimentoController(AtendimentoOrquestradorService orquestradorService) {
        this.orquestradorService = orquestradorService;
    }

    // endpoint simples para testar rapidamente a atualizacao dinamica do Config Server
    @GetMapping("/info")
    public String obterInfoConfiguracao() {
        return mensagemBoasVindas;
    }

    // endpoint principal que coordena a chamada GraphQL para o Microservico 2 e invocação do ms3
    @PostMapping("/atendimento")
    public AtendimentoResponse realizarAtendimento(@RequestBody AtendimentoRequest request) {
        // invoca ms3
        var resultadoValidacao = resilienciaAtiva
                ? orquestradorService.acionarLambdaValidacao(request)
                : orquestradorService.acionarLambdaValidacaoSemResiliencia(request);

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
        RespostaAtendimentoMs2 respostaMs2 = resilienciaAtiva
                ? orquestradorService.consultarAssistenteIa(request, resultadoValidacao)
                : orquestradorService.consultarAssistenteIaSemResiliencia(request, resultadoValidacao);

        String origem;
        if ("FALLBACK_MODO_OFFLINE".equals(respostaMs2.modeloUtilizado())) {
            origem = "RESPOSTA_DE_FALLBACK_RESILIENCE4J";
        } else if ("GEMINI-MOCK-MODE".equals(respostaMs2.modeloUtilizado())) {
            origem = "COMUNICACAO_GRAPHQL_MOCK_IA";
        } else {
            origem = "COMUNICACAO_GRAPHQL_SUCESSO";
        }

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