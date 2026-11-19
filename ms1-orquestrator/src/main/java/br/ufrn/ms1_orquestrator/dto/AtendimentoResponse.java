package br.ufrn.ms1_orquestrator.dto;

public record AtendimentoResponse(
        String mensagemSistema,
        String protocolo,
        boolean pedidoValido,
        String prioridade,
        String clienteId,
        String pedidoId,
        String respostaIa,
        String modeloUtilizado,
        String apiKeyMascarada,
        String origemDados
) {}