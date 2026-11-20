# Config Server (`config-server`)

Servidor de configuração centralizada ([src/main/resources/application.yaml](src/main/resources/application.yaml)) que entrega os arquivos de `config-repo/` para `api-gateway`, `ms1-orquestrator` e `ms2-ai-service`. Também registra-se no Eureka para descoberta automática pelo Prometheus.

---

## Modos de Funcionamento (Perfis)

- **Perfil `native` (padrão local):** lê os arquivos diretamente da pasta local `config-repo/`, evitando sobrescrever alterações ainda não enviadas ao Git.
- **Perfil `git` (recomendado na AWS):** clona os arquivos da pasta `config-repo` diretamente de um repositório Git remoto.

---

## Variáveis de Ambiente

### Configurações do próprio `config-server`

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8888` | Porta HTTP do Config Server. |
| `SPRING_PROFILES_ACTIVE` | `native` | Use `native` localmente ou `git` na AWS. |
| `CONFIG_GIT_URI` | `https://github.com/kaelpsu/trabalho3-distribuida.git` | URL do repositório Git (usada quando `SPRING_PROFILES_ACTIVE=git`). |
| `CONFIG_GIT_BRANCH` | `main` | Branch lida no modo `git`. |
| `CONFIG_NATIVE_LOCATIONS` | `file:./config-repo,file:../config-repo` | Caminhos locais buscados no modo `native`. |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/,http://localhost:8762/eureka/` | URLs dos servidores Eureka para registro deste serviço. |

### Variáveis resolvidas pelos clientes via `config-repo/application.yml`

Além das variáveis acima, o arquivo global [../config-repo/application.yml](../config-repo/application.yml) contém placeholders resolvidos pelos serviços clientes (`api-gateway`, `ms1-orquestrador`, `ms2-ai-service`):

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/,http://localhost:8762/eureka/` | URLs do cluster Eureka usadas pelos clientes. |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint do Zipkin para exportação de traces. |

*(Importante: essas variáveis precisam ser definidas no ambiente de cada microsserviço cliente ou diretamente em um perfil/commit no `config-repo/` se o valor for fixo por ambiente).*

---

## Como Executar

### Localmente (Desenvolvimento)

```bash
mvn -f config-server/ spring-boot:run
```

### Na AWS (EC2)

```bash
export SPRING_PROFILES_ACTIVE=git
export CONFIG_GIT_URI="https://github.com/kaelpsu/trabalho3-distribuida.git"
export CONFIG_GIT_BRANCH="main"
export EUREKA_DEFAULT_ZONE="http://<IP_EUREKA_PEER1>:8761/eureka/,http://<IP_EUREKA_PEER2>:8762/eureka/"

java -jar config_server-0.0.1-SNAPSHOT.jar
```

> **Security Groups na AWS:** libere a porta `8888/TCP` para os microsserviços (`api-gateway`, `ms1-orquestrador`, `ms2-ai-service`) e para o Prometheus.
