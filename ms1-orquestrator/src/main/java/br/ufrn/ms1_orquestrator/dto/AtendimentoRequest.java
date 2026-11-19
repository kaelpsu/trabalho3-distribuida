package br.ufrn.ms1_orquestrator.dto;

public record AtendimentoRequest(
        String clienteId,
        String pedidoId,
        String pergunta
) {}