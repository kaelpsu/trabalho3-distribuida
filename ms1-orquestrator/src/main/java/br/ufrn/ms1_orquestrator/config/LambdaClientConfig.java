package br.ufrn.ms1_orquestrator.config;

import br.ufrn.ms1_orquestrator.client.LambdaValidacaoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class LambdaClientConfig {

    @Bean
    @RefreshScope 
    public LambdaValidacaoClient lambdaValidacaoClient(
            RestClient.Builder defaultRestClientBuilder,
            @Value("${smartshop.lambda-url:http://localhost:8083}") String lambdaUrl) {

        RestClient restClient = defaultRestClientBuilder.clone()
                .baseUrl(lambdaUrl)
                .build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(LambdaValidacaoClient.class);
    }
}