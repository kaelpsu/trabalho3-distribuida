package br.ufrn.ms1_orquestrator.dto;

public record RespostaAtendimentoMs2(
        String protocolo,
        String clienteId,
        String pedidoId,
        String respostaIa,
        String modeloUtilizado,
        String apiKeyMascarada
) {}