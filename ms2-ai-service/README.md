# MS2 AI Service (`ms2-ai-service`)

Serviço GraphQL (porta `8082`) responsável pelo atendimento inteligente com **Spring AI 2.0.0** (Google Gemini nativo para chat e `gemini-embedding-001` para embeddings), memória de conversa por cliente e RAG em memória a partir de [src/main/resources/politica_smartshop.txt](src/main/resources/politica_smartshop.txt). Consome segredos diretamente do **HashiCorp Vault**.

Arquivos principais:
- Bootstrap local (Config Server + Vault): [src/main/resources/application.yml](src/main/resources/application.yml)
- Configuração remota: [../config-repo/ms2-ai-service.yml](../config-repo/ms2-ai-service.yml) e [../config-repo/application.yml](../config-repo/application.yml)

---

## Pré-requisito no HashiCorp Vault

Antes de iniciar o `ms2-ai-service`, o segredo `secret/ms2-ai-service` deve existir no Vault com as chaves:
- `gemini.api-key`: chave de API válida do Gemini (Google AI Studio / Gemini Developer API)
- `gemini.model`: nome do modelo (ex.: `gemini-3.5-flash-lite`)

Veja instruções completas em [../observability/README.md](../observability/README.md).

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `CONFIG_SERVER_URI` | `http://localhost:8888` | URL do Config Server. |
| `VAULT_HOST` | `localhost` | IP ou hostname onde o container do Vault está rodando. |
| `VAULT_PORT` | `8200` | Porta HTTP/HTTPS do Vault. |
| `VAULT_SCHEME` | `http` | Protocolo (`http` ou `https`). |
| `VAULT_TOKEN` | `root-token-trabalho3` | Token de autenticação no Vault. |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/,http://localhost:8762/eureka/` | URLs dos servidores Eureka. |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint do Zipkin na máquina de observabilidade. |

---

## Como Executar

### Localmente (Desenvolvimento)

```bash
mvn -f ms2-ai-service/ spring-boot:run
```

### Na AWS (EC2)

```bash
export CONFIG_SERVER_URI="http://<IP_EC2_CONFIG_SERVER>:8888"
export VAULT_HOST="<IP_EC2_OBSERVABILIDADE>"
export VAULT_PORT="8200"
export VAULT_SCHEME="http"
export VAULT_TOKEN="seu-token-vault-seguro"
export EUREKA_DEFAULT_ZONE="http://<IP_EUREKA_PEER1>:8761/eureka/,http://<IP_EUREKA_PEER2>:8762/eureka/"
export ZIPKIN_ENDPOINT="http://<IP_EC2_OBSERVABILIDADE>:9411/api/v2/spans"

java -jar ms2_ai_service-0.0.1-SNAPSHOT.jar
```

> **Security Groups na AWS:** libere entrada na porta `8082/TCP` para o `ms1-orquestrador` e para o Prometheus, e garanta saída da instância do `ms2-ai-service` para o Vault (`8200/TCP`) e para a internet (`443/TCP`, API do Gemini).
