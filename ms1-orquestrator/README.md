# MS1 Orquestrador (`ms1-orquestrator`)

Serviço orquestrador REST (porta `8081`) que expõe `/api/assistente/atendimento` e `/api/assistente/info`. Coordena a validação inicial do pedido no `ms3-lambda` e consulta o assistente de IA no `ms2-ai-service` via GraphQL (`http://ms2-ai-service/graphql` via Eureka LoadBalancer), utilizando Resilience4j (Circuit Breaker, Retry, Rate Limiter e Bulkhead).

Arquivos principais:
- Bootstrap local: [src/main/resources/application.yml](src/main/resources/application.yml)
- Configuração remota: [../config-repo/ms1-orquestrador.yml](../config-repo/ms1-orquestrador.yml) e [../config-repo/application.yml](../config-repo/application.yml)

---

## Perfil Ativo Padrão

O `ms1-orquestrador` inicia com `spring.profiles.active: chaos-monkey` para possibilitar testes de caos controlados (o Chaos Monkey inicia **desabilitado** por `chaos.monkey.enabled: false` no `config-repo/application.yml`).

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `CONFIG_SERVER_URI` | `http://localhost:8888` | URL do Config Server. |
| `LAMBDA_URL` | `http://localhost:8083/validarPedido` | URL completa do endpoint do `ms3-lambda`. Na AWS, informe a **Function URL** gerada pela AWS Lambda (ex.: `https://<id>.lambda-url.<regiao>.on.aws/`). |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/,http://localhost:8762/eureka/` | URLs dos servidores Eureka. |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint do Zipkin na máquina de observabilidade. |

> **Atualização dinâmica (`@RefreshScope`):** tanto a mensagem `smartshop.mensagem-boas-vindas` quanto o cliente HTTP de `smartshop.lambda-url` suportam atualização em tempo real via `POST http://<HOST_MS1>:8081/actuator/refresh`.

---

## Como Executar

### Localmente (Desenvolvimento)

```bash
mvn -f ms1-orquestrator/ spring-boot:run
```

### Na AWS (EC2)

```bash
export CONFIG_SERVER_URI="http://<IP_EC2_CONFIG_SERVER>:8888"
export EUREKA_DEFAULT_ZONE="http://<IP_EUREKA_PEER1>:8761/eureka/,http://<IP_EUREKA_PEER2>:8762/eureka/"
export ZIPKIN_ENDPOINT="http://<IP_EC2_OBSERVABILIDADE>:9411/api/v2/spans"
export LAMBDA_URL="https://<ID_DA_SUA_LAMBDA>.lambda-url.us-east-1.on.aws/"

java -jar ms1_orquestrator-0.0.1-SNAPSHOT.jar
```

> **Security Groups na AWS:** libere entrada na porta `8081/TCP` para o `api-gateway` e para o Prometheus.
