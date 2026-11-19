package br.ufrn.ms1_orquestrator.client;

import br.ufrn.ms1_orquestrator.dto.ValidacaoLambdaRequest;
import br.ufrn.ms1_orquestrator.dto.ValidacaoLambdaResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface LambdaValidacaoClient {

    @PostExchange
    ValidacaoLambdaResponse validarPedido(@RequestBody ValidacaoLambdaRequest request);
}