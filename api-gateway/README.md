# API Gateway (`api-gateway`)

Porta de entrada HTTP do sistema (porta `8080`). Roteia requisições `/api/assistente/**` para `lb://ms1-orquestrador` via Service Discovery (Eureka), aplicando o Circuit Breaker `ms1CircuitBreaker` com fallback em `/fallback/atendimento`.

Arquivos principais:
- Bootstrap local: [src/main/resources/application.yml](src/main/resources/application.yml)
- Configuração remota: [../config-repo/api-gateway.yml](../config-repo/api-gateway.yml) e [../config-repo/application.yml](../config-repo/application.yml)

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `CONFIG_SERVER_URI` | `http://localhost:8888` | URL do Config Server (usada no bootstrap antes de baixar as demais configs). |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/,http://localhost:8762/eureka/` | URLs dos servidores Eureka. |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint do Zipkin na máquina de observabilidade. |

---

## Como Executar

### Localmente (Desenvolvimento)

```bash
mvn -f api-gateway/ spring-boot:run
```

### Na AWS (EC2)

```bash
export CONFIG_SERVER_URI="http://<IP_EC2_CONFIG_SERVER>:8888"
export EUREKA_DEFAULT_ZONE="http://<IP_EUREKA_PEER1>:8761/eureka/,http://<IP_EUREKA_PEER2>:8762/eureka/"
export ZIPKIN_ENDPOINT="http://<IP_EC2_OBSERVABILIDADE>:9411/api/v2/spans"

java -jar api_gateway-0.0.1-SNAPSHOT.jar
```

> **Security Groups na AWS:** libere entrada na porta `8080/TCP` para os clientes externos (ou Load Balancer) e para o Prometheus raspar `/actuator/prometheus`.
