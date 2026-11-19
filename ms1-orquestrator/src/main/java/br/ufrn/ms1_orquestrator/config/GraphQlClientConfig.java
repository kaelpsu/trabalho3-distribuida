package br.ufrn.ms1_orquestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import br.ufrn.ms1_orquestrator.client.LambdaValidacaoClient;

import org.springframework.context.annotation.Primary;

@Configuration
public class GraphQlClientConfig {

    // Builder padrão (@Primary) sem LoadBalancer: usado internamente pelo Eureka Client
    // para acessar http://localhost:8761/eureka/ sem interceptação
    @Bean
    @Primary
    public RestClient.Builder defaultRestClientBuilder(
            org.springframework.beans.factory.ObjectProvider<org.springframework.boot.restclient.RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean
    @LoadBalanced // usado pra resolver nomes com o eureka
    public RestClient.Builder loadBalancedRestClientBuilder(
            org.springframework.beans.factory.ObjectProvider<org.springframework.boot.restclient.RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder();
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean
    public HttpSyncGraphQlClient httpSyncGraphQlClient(@LoadBalanced RestClient.Builder builder) {
        RestClient restClient = builder
                .baseUrl("http://ms2-ai-service/graphql")
                .build();
        return HttpSyncGraphQlClient.create(restClient);
    }

    @Bean
    public LambdaValidacaoClient lambdaValidacaoClient(
            RestClient.Builder defaultBuilder,
            @Value("${smartshop.lambda-url:http://localhost:8083/validarPedido}") String lambdaUrl) {

        RestClient restClient = defaultBuilder.clone()
                .baseUrl(lambdaUrl)
                .build();

        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(LambdaValidacaoClient.class);
    }
}