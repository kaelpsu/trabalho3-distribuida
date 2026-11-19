package br.ufrn.ms1_orquestrator.dto;

public record ValidacaoLambdaResponse(
        String protocolo,
        boolean pedidoValido,
        String prioridade,
        String processadoEm
) {}