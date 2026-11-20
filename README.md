# Sistema de Atendimento Inteligente SmartShop (Trabalho 3)

Arquitetura de microsserviços com **Spring Boot 4.1.2**, **Spring Cloud 2025.1.4** e **Spring AI 2.0.0**, incluindo Service Discovery (Eureka em cluster peer), configuração centralizada (Config Server), gerenciamento de segredos (Vault), resiliência (Resilience4j + Chaos Monkey), validação serverless (`ms3-lambda`) e observabilidade (Prometheus, Grafana e Zipkin).

---

## Visão Geral dos Módulos

| Módulo | Porta padrão | Função | Guia de Configuração |
| :--- | :---: | :--- | :--- |
| `eureka-server` | `8761` / `8762` | Service Discovery em cluster (`peer1` e `peer2`) | [eureka-server/README.md](eureka-server/README.md) |
| `config-server` | `8888` | Servidor de configurações (`config-repo/`) | [config-server/README.md](config-server/README.md) |
| `api-gateway` | `8080` | Gateway HTTP com Circuit Breaker (`ms1CircuitBreaker`) | [api-gateway/README.md](api-gateway/README.md) |
| `ms1-orquestrator` | `8081` | Orquestrador REST que aciona `ms3-lambda` e `ms2-ai-service` | [ms1-orquestrator/README.md](ms1-orquestrator/README.md) |
| `ms2-ai-service` | `8082` | Serviço GraphQL com Spring AI (Gemini) e RAG | [ms2-ai-service/README.md](ms2-ai-service/README.md) |
| `ms3-lambda` | `8083` / AWS Lambda | Função serverless `validarPedido` (Spring Cloud Function) | [ms3-lambda/README.md](ms3-lambda/README.md) |
| `observability` + `docker-compose.yml` | `8200`, `9090`, `3000`, `9411` | Vault, Prometheus, Grafana e Zipkin | [observability/README.md](observability/README.md) |

---

## Execução Local (Desenvolvimento)

Todas as propriedades já possuem valores padrão para `localhost`, permitindo rodar localmente sem configurar variáveis de ambiente. A execução local pode ser automatizada por meio de um script como:

```bash
./start-trabalho3.sh
```

O script sobe os containers Docker (`vault`, `prometheus`, `grafana`, `zipkin`), grava a chave padrão no Vault e abre uma aba no terminal para cada serviço Java. Obs: pode precisar ser adaptado para seu ambiente.

---

## Estratégia de Deploy na AWS

A separação entre ambiente local e produção usa **variáveis de ambiente com fallback local** (`${VAR:valor_local}`) nos `.yml` e perfis do Spring (`native`/`git` no Config Server e `prometheus.yml`/`prometheus-prod.yml` no Prometheus):

- **Máquina de Observabilidade e Segredos (Docker Compose):** executa `vault`, `prometheus` (com `PROMETHEUS_CONFIG_FILE=prometheus-prod.yml` via Eureka), `grafana` e `zipkin`.
- **Instâncias EC2 individuais:** executam `eureka-server` (`peer1` e `peer2`), `config-server` (com `SPRING_PROFILES_ACTIVE=git`), `api-gateway`, `ms1-orquestrator` e `ms2-ai-service`.
- **AWS Lambda:** executa o pacote `ms3-lambda/target/ms3_lambda-0.0.1-SNAPSHOT-aws.jar` com **Function URL** configurada em `LAMBDA_URL` no `ms1-orquestrator`.

### Ordem Recomendada de Inicialização na AWS

1. **Docker Compose** (máquina de observabilidade/segredos): Vault, Prometheus, Grafana e Zipkin — e popular o segredo do Gemini no Vault.
2. **Eureka Server (`peer1` e `peer2`)**.
3. **Config Server** (com `SPRING_PROFILES_ACTIVE=git`).
4. **AWS Lambda (`ms3-lambda`)** (publicar e anotar a Function URL).
5. **Microsserviços de aplicação**: `ms2-ai-service`, `ms1-orquestrator` e `api-gateway`.

> **Rede (AWS Security Groups):** como cada serviço roda em uma máquina distinta dentro da VPC, libere o tráfego TCP entre as instâncias para as portas configuradas (`8761`, `8762`, `8888`, `8080`, `8081`, `8082`, `8200`, `9090`, `3000`, `9411`) e utilize **IPs privados** nas variáveis de ambiente.

---

## Teste Rápido (via API Gateway)

```bash
curl -X POST http://<HOST_GATEWAY>:8080/api/assistente/atendimento \
  -H "Content-Type: application/json" \
  -d '{
    "clienteId": "cli-001",
    "pedidoId": "PED-1001",
    "pergunta": "Qual o prazo de troca para eletrônicos?"
  }'
```
