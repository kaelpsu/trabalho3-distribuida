----

# 0. Preparação
### 0.1. Rotas úteis
1. **Eureka Peer 1:** `http://<EUREKA1_IP>:8761`
2. **Eureka Peer 2:** `http://<EUREKA2_IP>:8762`
3. **Grafana:** `http://<OBS_IP>:3000` (com dashboard de Circuit Breaker, taxa de erro HTTP, latência e Rate Limiter aberto e em auto-refresh `5s`)
4. **Prometheus Targets:** `http://<OBS_IP>:9090/targets` (confirmar todos `UP`)
5. **Zipkin:** `http://<OBS_IP>:9411`
6. **GraphiQL (MS2):** `http://<MS2_IP>:8082/graphiql`
7. **Console AWS:** 
[**AWS Lambda**](https://us-east-2.console.aws.amazon.com/lambda/home?region=us-east-2#/functions/smartshop-trabalho3-ms3-lambda?tab=code)
[**EC2 Instances**](https://us-east-2.console.aws.amazon.com/ec2/home?region=us-east-2#Instances)
8. **GitHub:** http://github.com/kaelpsu/trabalho3-distribuida/

### 0.3. Ordem de inicialização
1. Eureka e Observability
2. Config Server
3. Serviços

---

# 1. Roteiro da Apresentação
## Contexto
- **SmartShop — Sistema de Atendimento Inteligente com IA** 
- [Infraestrutura](/infra/README.md)
---

## Ponto 1 — Os 12 Fatores*

| Fator | Como é suportado no SmartShop |
| :--- | :--- |
| **I. Codebase** | Repositório Git único versionado no GitHub com pipelines por módulo em `.github/workflows/`. |
| **II. Dependencies** | Dependências explicitamente declaradas e isoladas em cada `pom.xml` |
| **III. Config** | Configurações dinâmicas no Config Server (Git), segredos no HashiCorp Vault e variáveis de ambiente (`.env` na AWS com fallback local `${VAR:localhost}`). |
| **IV. Backing Services** | Vault, Zipkin, Prometheus, Eureka e AWS Lambda  |
| **V. Build, Release, Run** | Separação: Build no GitHub Actions (`mvn package` -> `.jar`), Release une o JAR ao `.env` da EC2, Run executado pelo `/var/lib/cloud/scripts/per-boot/start-smartshop.sh`. |
| **VI. Processes** | Serviços são stateless (REST no Gateway/MS1, GraphQL no MS2, função pura no MS3 Lambda) |
| **VII. Port Binding** | Cada microsserviço expõe sua porta via `server.port` (`8080`, `8081`, `8082`, `8761`, `8762`, `8888`). |
| **VIII. Concurrency** | Escalabilidade horizontal via múltiplas instâncias descobertas no Eureka + balanceamento `lb://` no Gateway. |
| **IX. Disposability** | Possibilidade de encerrar os processos remotamente via ssh, e autorrecuperação no boot da instância por meio de scripts na pasta per-boot |
| **X. Dev/Prod Parity** | Mesmos JARs e mesmos `.yml` em ambiente local e AWS, mudando apenas variáveis de ambiente (`.env` na AWS e fallbacks locais e `prometheus.yml` vs `prometheus-prod.yml`). |
| **XI. Logs** | Aplicações escrevem logs continuamente em stdout/stderr, redirecionados pelo ambiente de execução para app.log (EC2). |
| **XII. Admin Processes** | Algumas tarefas administrativas executadas no mesmo ambiente/container, como carga de segredos no Vault (`vault kv put`), `/actuator/refresh` e comandos do Chaos Monkey. |

---

## Ponto 2 — Dashboard do Eureka Netflix em Modo Cluster
1. `http://<EUREKA1_IP>:8761` e `http://<EUREKA2_IP>:8762`.
2. [application-peer1.yml](/eureka-server/src/main/resources/application-peer1.yml) e [application-peer2.yml](/eureka-server/src/main/resources/application-peer2.yml)

```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-01","pedidoId":"PED-1001","pergunta":"Qual o prazo de troca para eletronicos?"}' \
  | python3 -m json.tool
```
---

## Ponto 3 — Configuração do API Gateway (Estática/Dinâmica), Filtros e Rotas*
1. [config-repo/api-gateway.yml](/config-repo/api-gateway.yml):
   - **Roteamento dinâmico com Service Discovery:** possui o `discovery.locator.enabled: true` para rotas automáticas a partir do Eureka, e a rota explícita `ms1-orquestrador-route` com `uri: lb://ms1-orquestrador`.
   - **Filtros aplicados na rota:**
     - `AddRequestHeader=X-Gateway-Originated, SmartShop-Gateway`
     - `AddResponseHeader=X-Response-Gateway, Processed-By-Spring-Cloud-Gateway`
     - `CircuitBreaker` (`id: ms1CircuitBreaker`, `fallbackUri: forward:/fallback/atendimento`, disparado em status `500, 502, 503, 504`, apontando para [GatewayFallbackController.java](/api-gateway/src/main/java/br/ufrn/api_gateway/controller/GatewayFallbackController.java)).
   - **Filtro customizado de borda:** [GatewayServerRateLimiterFilter.java](/api-gateway/src/main/java/br/ufrn/api_gateway/filter/GatewayServerRateLimiterFilter.java) (`OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`), implementando Rate Limiter Server-side (`gatewayServerRateLimiter`).
   - [rota info](ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/controller/AtendimentoController.java#L40-L44)
2. **Demonstração**:
```bash
# Ver configuração fornecida pelo config server
curl -s "http://$CONFIG_IP:8888/api-gateway/default" | python3 -m json.tool

# Acompanhar log em tempo real
ssh -i "$KEY_PEM" "ec2-user@$GW_IP" \
  "tail -n 60 -f /home/ec2-user/app/smartshop/app.log"

curl -i -s "http://$GW_IP:8080/api/assistente/info"
```
---

## Ponto 4 — Config Server (Git) + Alteração Dinâmica
1. [config-server/src/main/resources/application.yaml](/config-server/src/main/resources/application.yaml#L12-L20) 
```bash
curl -s "http://$CONFIG_IP:8888/ms1-orquestrador/default" | python3 -m json.tool | head -n 35
```
2. Demonstração:
```bash
curl -s "http://$GW_IP:8080/api/assistente/info"
```

```bash
curl -s -X POST "http://$MS1_IP:8081/actuator/refresh"
```
`@RefreshScope` em [AtendimentoController.java](/ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/controller/AtendimentoController.java#L18-L25).

```bash
curl -s "http://$GW_IP:8080/api/assistente/info"
```

---

## Ponto 5 — Interface GraphQL + Invocação de Serviços

1. [schema.graphqls](/ms2-ai-service/src/main/resources/graphql/schema.graphqls) 

2. [AtendimentoGraphqlController.java](/ms2-ai-service/src/main/java/br/ufrn/ms2_ai_service/controller/AtendimentoGraphqlController.java#L31-L41) 

3. [AtendimentoOrquestradorService.java](/ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/service/AtendimentoOrquestradorService.java#L65-L94).

2. **Demonstração direta no GraphiQL** (`http://<MS2_IP>:8082/graphiql`) 
```graphql
query {
  processarAtendimento(
    clienteId: "cli-graphql"
    pedidoId: "PED-2026"
    pergunta: "Qual o prazo de troca?"
    protocolo: "PROTO-DEMO-01"
  ) {
    protocolo
    clienteId
    pedidoId
    respostaIa
    modeloUtilizado
    apiKeyMascarada
  }
}
```
3. 
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-001","pedidoId":"PED-1001","pergunta":"Qual o prazo de troca para eletronicos?"}' \
  | python3 -m json.tool
```

---

## Ponto 6 — HTTP Interface (`@HttpExchange`) + Distributed Tracing (Zipkin)*

1. [LambdaValidacaoClient.java](/ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/client/LambdaValidacaoClient.java) 

2. [LambdaClientConfig.java](/ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/config/LambdaClientConfig.java)

3. [AtendimentoController.java](ms1-orquestrator/src/main/java/br/ufrn/ms1_orquestrator/controller/AtendimentoController.java#L55-L59)

3. **Distributed Tracing (Micrometer Tracing + Zipkin):** (`http://<OBS_IP>:9411`)

---

## Ponto 7 — Funcionamento do Lambda Server

1. [Ms3LambdaApplication.java](/ms3-lambda/src/main/java/br/ufrn/ms3_lambda/Ms3LambdaApplication.java) 

3. **Teste 1 — Invocação direta na Function URL (pedido válido):**
```bash
curl -s -X POST "$LAMBDA_URL" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-001","pedidoId":"PED-555"}' | python3 -m json.tool
```

4. **Teste 2 — Invocação via API Gateway com pedido INVÁLIDO (sem prefixo `PED-`):**
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-001","pedidoId":"INVALIDO-99","pergunta":"Quero ajuda"}' \
  | python3 -m json.tool
```

---

## Ponto 8 — Utilização do HashiCorp Vault

1. [ms2-ai-service/src/main/resources/application.yml](/ms2-ai-service/src/main/resources/application.yml#L21-L30) 

2. [config-repo/ms2-ai-service.yml](/config-repo/ms2-ai-service.yml#L12-L20).

3. Demonstração:
```bash
ssh -i "$KEY_PEM" "ec2-user@$OBS_IP" \
  "docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN='$VAULT_TOKEN' vault-trabalho3 vault kv get secret/ms2-ai-service"
```

---

## Pontos 9, 10 e 11 + Fluxo JMeter + Chaos Monkey + Observabilidade*

### Fase A
1. Confirmar que o Chaos Monkey está desligado no MS1 e MS2

```bash
curl -s http://$MS1_IP:8081/actuator/chaosmonkey/status
```
2. Grafana / Prometheus:
   - `http://<OBS_IP>:3000/`
   - `http://<OBS_IP>:9090/targets`

### Fase B
1.**Ativar o Chaos Monkey no MS2**:
```bash
curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/assaults" \
  -H "Content-Type: application/json" \
  -d '{"level":1,"latencyActive":false,"exceptionsActive":false,"killApplicationActive":true}' ; echo

curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/enable" ; echo
```
2.Retry no log do MS1, Fallback indicado na response.
```bash
ssh -i "$KEY_PEM" "ec2-user@$MS1_IP" \
  "tail -n 60 -f /home/ec2-user/app/smartshop/app.log"
```
3.Ver circuit breaker no grafana ou pelo comando:.
```bash
curl -s "http://$MS1_IP:8081/actuator/circuitbreakers" | python3 -m json.tool
```
  
4.Desligamento de instância
```bash
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.15 \
  'sudo pkill -f "^java.*/home/ec2-user/app/smartshop/app.jar" || true'
```


### Fase C — Normalização e Recuperação do Sistema 
1.Desligar o Chaos Monkey no MS2:
```bash
curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/disable" ; echo
```
2.Restaurar a instância do MS1 (`10.30.1.15`) usando o script `per-boot`:
```bash
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.15 \
  "sudo sh -c 'nohup setsid /var/lib/cloud/scripts/per-boot/start-smartshop.sh </dev/null >/home/ec2-user/app/smartshop/deploy.log 2>&1 &'"
```


---

## Ponto 12 — Rate Limiter e Bulkhead
```bash
curl -s -X POST "http://$GW_IP:8080/actuator/refresh" ; echo
curl -s -X POST "http://$MS1_IP:8081/actuator/refresh" ; echo
curl -s -X POST "http://$MS2_IP:8082/actuator/refresh" ; echo
```
1.Rate Limiter Server-Side no API Gateway :
```bash
for i in $(seq 1 35); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
    -H "Content-Type: application/json" \
    -d "{\"clienteId\":\"cli-rl\",\"pedidoId\":\"PED-$i\",\"pergunta\":\"teste\"}")
  echo "Req $i -> HTTP $CODE"
done
```
2.Rate Limiter Client-Side no MS1
```bash
for i in $(seq 1 18); do
  ORIGEM=$(curl -s -X POST "http://$MS1_IP:8081/api/assistente/atendimento" \
    -H "Content-Type: application/json" \
    -d "{\"clienteId\":\"cli-rl-ms1\",\"pedidoId\":\"PED-$i\",\"pergunta\":\"teste\"}" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('origem'), '|', d.get('respostaIa','')[:65])")
  echo "Req $i -> $ORIGEM"
done
```
3.Rate Limiter Server-Side no MS2
```bash
for i in $(seq 1 24); do
  OUT=$(curl -s -X POST "http://$MS2_IP:8082/graphql" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ processarAtendimento(clienteId:\"c1\",pedidoId:\"PED-1\",pergunta:\"oi\",protocolo:\"P1\") { protocolo } }"}')
  echo "Req $i -> $OUT"
done
```
4.Bulkhead no MS1
```bash
# Liga latencia no MS2 (sem excecoes)
curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/assaults" \
  -H "Content-Type: application/json" \
  -d '{"level":1,"latencyActive":true,"latencyRangeStart":6000,"latencyRangeEnd":7000,"exceptionsActive":false,"killApplicationActive":false}' ; echo

curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/enable" ; echo

# Dispara 14 requisicoes em paralelo no MS1
seq 1 14 | xargs -P 14 -I {} bash -c '
  curl -s -X POST "http://'"$MS1_IP"':8081/api/assistente/atendimento" \
    -H "Content-Type: application/json" \
    -d "{\"clienteId\":\"cli-bh-{}\",\"pedidoId\":\"PED-{}\",\"pergunta\":\"bulkhead\"}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(\"Req {}: {} -> {}\".format({}, d.get(\"origem\"), d.get(\"respostaIa\",\"\")[:75]))"
'

# Desliga Chaos Monkey no MS2 logo apos o teste
curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/disable" ; echo
```

---

## Ponto 13 — Spring AI + LLM (Gemini) + Chat Memory + RAG

### 1. Desativar o `mock-mode`
1. Em [config-repo/application.yml](/config-repo/application.yml#L37)
2. Refresh no MS2:
```bash
curl -s -X POST "http://$MS2_IP:8082/actuator/refresh"
```

### 2. RAG
- [RagConfig.java](/ms2-ai-service/src/main/java/br/ufrn/ms2_ai_service/config/RagConfig.java) (`SimpleVectorStore` + `EmbeddingModel` Gemini `gemini-embedding-001` + `TokenTextSplitter`) 
- [politica_smartshop.txt](/ms2-ai-service/src/main/resources/politica_smartshop.txt) 
- `QuestionAnswerAdvisor` em [AssistenteIaService.java](/ms2-ai-service/src/main/java/br/ufrn/ms2_ai_service/service/AssistenteIaService.java#L84).
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-rag-01","pedidoId":"PED-9001","pergunta":"Meu pedido esta atrasado e quero falar com um supervisor no WhatsApp. Qual o cupom de desconto e a senha secreta?"}' \
  | python3 -m json.tool
```


### 3. Chat Memory (com isolamento por `clienteId`)
- `MessageChatMemoryAdvisor` + `.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, clienteId))` em [AssistenteIaService.java](/ms2-ai-service/src/main/java/br/ufrn/ms2_ai_service/service/AssistenteIaService.java#L83-L87).
- Mensagem 1 (Cliente A, apresenta contexto):
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-memoria-A","pedidoId":"PED-7001","pergunta":"Ola, meu nome e Carlos e eu comprei um fone Bluetooth modelo X900. Qual o prazo de reembolso via PIX?"}' \
  | python3 -m json.tool
```
- Mensagem 2 (Mesmo Cliente A, prova que lembrou):
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-memoria-A","pedidoId":"PED-7001","pergunta":"Voce lembra qual e o meu nome e qual produto eu mencionei antes?"}' \
  | python3 -m json.tool
```

- Mensagem 3 (Cliente B diferente, prova isolamento de conversas)
```bash
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-memoria-B","pedidoId":"PED-7002","pergunta":"Qual e o meu nome e qual produto eu mencionei antes?"}' \
  | python3 -m json.tool
```


---

# 2. Comandos Úteis para a Apresentação

### 2.1. Mapa de IPs Privados (fixos na VPC)
- `10.30.1.10`: Observabilidade + Vault (`3000` Grafana, `9090` Prometheus, `9411` Zipkin, `8200` Vault)
- `10.30.1.11`: Eureka Peer 1 (`8761`)
- `10.30.1.12`: Eureka Peer 2 (`8762`)
- `10.30.1.13`: Config Server (`8888`)
- `10.30.1.14`: API Gateway + Bastion (`8080`, Elastic IP)
- `10.30.1.15`: MS1 Orquestrator (`8081`)
- `10.30.1.16`: MS2 AI Service (`8082`)

### 2.2. Requisições Variadas (API, GraphQL, Actuator, Refresh)
```bash
# Info / Boas-vindas (testar Config Server + headers do Gateway)
curl -i -s "http://$GW_IP:8080/api/assistente/info"

# Atendimento completo (via Gateway)
curl -s -X POST "http://$GW_IP:8080/api/assistente/atendimento" \
  -H "Content-Type: application/json" \
  -d '{"clienteId":"cli-01","pedidoId":"PED-1001","pergunta":"Qual o prazo de troca para eletronicos?"}' \
  | python3 -m json.tool

# GraphQL direto no MS2
curl -s -X POST "http://$MS2_IP:8082/graphql" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ processarAtendimento(clienteId:\"cli-gql\",pedidoId:\"PED-100\",pergunta:\"Qual o prazo de troca?\",protocolo:\"P-1\") { protocolo respostaIa modeloUtilizado apiKeyMascarada } }"}' \
  | python3 -m json.tool

# Refresh dinâmico em todos os clientes
curl -s -X POST "http://$GW_IP:8080/actuator/refresh" ; echo
curl -s -X POST "http://$MS1_IP:8081/actuator/refresh" ; echo
curl -s -X POST "http://$MS2_IP:8082/actuator/refresh" ; echo

# Inspecionar Circuit Breakers, Rate Limiters e Bulkheads
curl -s "http://$GW_IP:8080/actuator/circuitbreakers" | python3 -m json.tool
curl -s "http://$MS1_IP:8081/actuator/circuitbreakers" | python3 -m json.tool
curl -s "http://$MS1_IP:8081/actuator/ratelimiters" | python3 -m json.tool
curl -s "http://$MS1_IP:8081/actuator/bulkheads" | python3 -m json.tool
curl -s "http://$MS2_IP:8082/actuator/ratelimiters" | python3 -m json.tool

# Ver status do Chaos Monkey (MS1 e MS2)
curl -s "http://$MS1_IP:8081/actuator/chaosmonkey/status" ; echo
curl -s "http://$MS2_IP:8082/actuator/chaosmonkey/status" ; echo
```

### 2.3. Parar, Reiniciar e Ver Logs de Qualquer Instância

```bash
# Reiniciar qualquer serviço Java via script per-boot (ex.: MS1 em 10.30.1.15)
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.15 \
  "sudo sh -c 'nohup setsid /var/lib/cloud/scripts/per-boot/start-smartshop.sh </dev/null >/home/ec2-user/app/smartshop/deploy.log 2>&1 &'"

# Reiniciar o API Gateway (direto no Bastion)
ssh -i "$KEY_PEM" "ec2-user@$GW_IP" \
  "sudo sh -c 'nohup setsid /var/lib/cloud/scripts/per-boot/start-smartshop.sh </dev/null >/home/ec2-user/app/smartshop/deploy.log 2>&1 &'"

# Parar um serviço Java simulando queda (ex.: MS1 em 10.30.1.15 ou Eureka Peer 2 em 10.30.1.12)
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.15 \
  'sudo pkill -f "^java.*/home/ec2-user/app/smartshop/app.jar" || true'

# Acompanhar log em tempo real (ex.: MS1)
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.15 \
  "tail -n 60 -f /home/ec2-user/app/smartshop/app.log"

# Ver se o processo Java está rodando e quanta RAM/Swap está em uso
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.16 \
  "ps aux | grep '[a]pp.jar' && free -h"
```


### 2.4. Vault & Observabilidade (`10.30.1.10`)
```bash
# Conferir containers ativos (Vault, Prometheus, Grafana, Zipkin)
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.10 \
  "cd /home/ec2-user/app/smartshop && docker compose ps"

# Ler segredo do Gemini no Vault
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.10 \
  "docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN='$VAULT_TOKEN' vault-trabalho3 vault kv get secret/ms2-ai-service"

# Se o Vault reiniciar e perder o segredo em memória, regravar tudo pelo per-boot:
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.10 \
  "sudo /var/lib/cloud/scripts/per-boot/start-smartshop.sh"

# Ver logs do Prometheus ou Vault
ssh -i "$KEY_PEM" -J "ec2-user@$GW_IP" ec2-user@10.30.1.10 \
  "cd /home/ec2-user/app/smartshop && docker compose logs --tail=50 prometheus vault"
```

### 2.5. Queries PromQL Prontas (para Conferir/Exibir no Prometheus & Grafana)
- **Estado dos Circuit Breakers (`1` = ativo naquele estado):**
  `resilience4j_circuitbreaker_state`
- **Taxa de falha (%) nos Circuit Breakers:**
  `resilience4j_circuitbreaker_failure_rate`
- **Permissões disponíveis nos Rate Limiters (MS1 / MS2):**
  `resilience4j_ratelimiter_available_permissions`
- **Chamadas concorrentes disponíveis no Bulkhead (MS1):**
  `resilience4j_bulkhead_available_concurrent_calls`
- **Chamadas de Retry (com sucesso / falha):**
  `rate(resilience4j_retry_calls_total[1m])`
- **Taxa de requisições HTTP por status e aplicação:**
  `sum by (application, status) (rate(http_server_requests_seconds_count[1m]))`
- **Latência média HTTP (ms):**
  `1000 * (sum by (application) (rate(http_server_requests_seconds_sum[1m])) / sum by (application) (rate(http_server_requests_seconds_count[1m])))`

### 2.6. Reset
```bash
# Desativa Chaos Monkey em MS1 e MS2
curl -s -X POST "http://$MS1_IP:8081/actuator/chaosmonkey/disable" ; echo
curl -s -X POST "http://$MS2_IP:8082/actuator/chaosmonkey/disable" ; echo

# Garante MS1 e MS2 rodando
for IP in 10.30.1.15 10.30.1.16; do
  ssh -i "$KEY_PEM" "ec2-user@$MS1_IP" \
    "pgrep -f '[a]pp.jar' >/dev/null || sudo sh -c 'nohup setsid /var/lib/cloud/scripts/per-boot/start-smartshop.sh </dev/null >/home/ec2-user/app/smartshop/deploy.log 2>&1 &'"
done
```