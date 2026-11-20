# Eureka Server (`eureka-server`)

Serviço de **Service Discovery** configurado em alta disponibilidade com dois perfis (`peer1` e `peer2`), que se registram mutuamente.

---

## Perfis e Portas

- `peer1`: arquivo [src/main/resources/application-peer1.yml](src/main/resources/application-peer1.yml) — porta padrão `8761`
- `peer2`: arquivo [src/main/resources/application-peer2.yml](src/main/resources/application-peer2.yml) — porta padrão `8762`
- Base comum: [src/main/resources/application.yml](src/main/resources/application.yml) (expõe `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/actuator/metrics`)

---

## Variáveis de Ambiente

| Variável | Padrão (`peer1` / `peer2`) | Descrição |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8761` / `8762` | Porta HTTP da instância Eureka. |
| `EUREKA_PEER1_HOST` | `localhost` | Hostname ou IP privado do `peer1`. |
| `EUREKA_PEER2_HOST` | `127.0.0.1` | Hostname ou IP privado do `peer2`. |
| `EUREKA_PEER1_PORT` | `8761` | Porta onde o `peer1` está acessível (usada pelo `peer2`). |
| `EUREKA_PEER2_PORT` | `8762` | Porta onde o `peer2` está acessível (usada pelo `peer1`). |
| `EUREKA_PREFER_IP` | `false` | Em produção na AWS, defina `true` para anunciar o IP da instância EC2. |

---

## Como Executar

### Localmente (Desenvolvimento)

```bash
# Terminal 1 (Peer 1)
mvn -f eureka-server/ spring-boot:run -Dspring-boot.run.profiles=peer1

# Terminal 2 (Peer 2)
mvn -f eureka-server/ spring-boot:run -Dspring-boot.run.profiles=peer2
```

### Na AWS (EC2s Separadas)

**Na instância do Peer 1:**
```bash
export SPRING_PROFILES_ACTIVE=peer1
export EUREKA_PEER1_HOST="<IP_PRIVADO_EC2_PEER1>"
export EUREKA_PEER2_HOST="<IP_PRIVADO_EC2_PEER2>"
export EUREKA_PREFER_IP=true

java -jar eureka_server-0.0.1-SNAPSHOT.jar
```

**Na instância do Peer 2:**
```bash
export SPRING_PROFILES_ACTIVE=peer2
export EUREKA_PEER1_HOST="<IP_PRIVADO_EC2_PEER1>"
export EUREKA_PEER2_HOST="<IP_PRIVADO_EC2_PEER2>"
export EUREKA_PREFER_IP=true

java -jar eureka_server-0.0.1-SNAPSHOT.jar
```

> **Security Groups na AWS:** libere `8761/TCP` e `8762/TCP` entre os peers, os microsserviços, o `config-server` e a instância do Prometheus.
