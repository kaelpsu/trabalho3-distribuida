# Observabilidade e Segredos (Vault, Prometheus, Grafana e Zipkin)

Este diretório concentra as configurações do **Prometheus** ([prometheus.yml](prometheus.yml) para uso local e [prometheus-prod.yml](prometheus-prod.yml) para produção via Eureka). Na raiz do repositório, o arquivo [../docker-compose.yml](../docker-compose.yml) sobe os 4 serviços juntos em uma única máquina:

- **HashiCorp Vault**: porta `8200`
- **Prometheus**: porta `9090` (`network_mode: host`)
- **Grafana**: porta `3000` (`network_mode: host`)
- **Zipkin**: porta `9411`

---

## Variáveis de Ambiente do Docker Compose

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `PROMETHEUS_CONFIG_FILE` | `prometheus.yml` | Arquivo dentro de `observability/` usado pelo Prometheus. Em produção na AWS, defina `prometheus-prod.yml`. |
| `VAULT_TOKEN` | `root-token-trabalho3` | Token raiz do Vault em modo dev. |
| `GRAFANA_USER` | `admin` | Usuário administrador do Grafana. |
| `GRAFANA_PASSWORD` | `admin` | Senha do administrador do Grafana. |

---

## Configuração para Produção na AWS

### 1. Editar os endereços do Eureka em `prometheus-prod.yml`

Antes de subir a stack na AWS, atualize [prometheus-prod.yml](prometheus-prod.yml) com os IPs privados (ou DNS) das instâncias onde rodam os servidores Eureka:

```yaml
eureka_sd_configs:
  - server: 'http://<IP_EC2_EUREKA_PEER1>:8761/eureka'
  - server: 'http://<IP_EC2_EUREKA_PEER2>:8762/eureka'
```

O Prometheus descobrirá automaticamente `api-gateway`, `ms1-orquestrador`, `ms2-ai-service`, `config-server` e `eureka-server` registrados no Eureka, raspando `/actuator/prometheus`.

### 2. Subir os containers na EC2 de observabilidade

```bash
export PROMETHEUS_CONFIG_FILE=prometheus-prod.yml
export VAULT_TOKEN="seu-token-vault-seguro"
export GRAFANA_USER="admin"
export GRAFANA_PASSWORD="sua-senha-grafana-segura"

docker compose up -d
```

### 3. Popular os segredos do Gemini no Vault

O `ms2-ai-service` lê o caminho `secret/ms2-ai-service` com as chaves `gemini.api-key` e `gemini.model`:

```bash
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' \
  -e VAULT_TOKEN="$VAULT_TOKEN" \
  vault-trabalho3 \
  vault kv put secret/ms2-ai-service \
  gemini.api-key="SUA_CHAVE_API_GEMINI" \
  gemini.model="gemini-3.5-flash-lite"
```

---

## Portas no Security Group da AWS

Garanta que a instância EC2 onde roda este `docker-compose.yml` permita:

- **Entrada da VPC (ou microsserviços):**
  - `8200/TCP` (Vault — acesso pelo `ms2-ai-service`)
  - `9411/TCP` (Zipkin — envio de spans pelo `api-gateway`, `ms1-orquestrador`, `ms2-ai-service` e, se habilitado, `ms3-lambda`)
- **Saída para as instâncias dos microsserviços:**
  - `8761`, `8762`, `8888`, `8080`, `8081`, `8082` (para o Prometheus consultar o Eureka e raspar `/actuator/prometheus`)
- **Acesso administrativo / visualização:**
  - `3000/TCP` (Grafana)
  - `9090/TCP` (Prometheus)
  - `9411/TCP` (Zipkin UI)
