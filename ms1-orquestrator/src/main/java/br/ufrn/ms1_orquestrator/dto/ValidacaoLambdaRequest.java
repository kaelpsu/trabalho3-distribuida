package br.ufrn.ms1_orquestrator.dto;

public record ValidacaoLambdaRequest(
        String clienteId,
        String pedidoId
) {}