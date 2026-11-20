# MS3 Lambda (`ms3-lambda`)

Função serverless construída com **Spring Cloud Function** (`validarPedido`) que valida se o `pedidoId` começa com `PED-` e se `clienteId` está preenchido, gerando protocolo e prioridade. Este serviço **não depende do Config Server nem do Eureka**.

---

## Variáveis de Ambiente

| Variável | Padrão | Descrição |
| :--- | :--- | :--- |
| `SPRING_CLOUD_FUNCTION_DEFINITION` | `validarPedido` | Nome do bean da função exposta (já definido em [application.yml](src/main/resources/application.yml)). |
| `TRACING_ENABLED` | `true` | Em execução na **AWS Lambda** sem acesso à rede privada do Zipkin, recomenda-se definir como `false` para evitar tentativas de envio e timeouts no span exporter. |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` | Endpoint do Zipkin (usado quando `TRACING_ENABLED=true`). |

---

## Modo 1: Deploy na AWS Lambda (Serverless)

### 1. Gerar o pacote compatível com a AWS Lambda

O [pom.xml](pom.xml) usa o `maven-shade-plugin` para gerar um JAR específico para a AWS Lambda com sufixo `-aws`:

```bash
mvn -f ms3-lambda/ clean package -DskipTests
```

Serão gerados dois arquivos em `ms3-lambda/target/`:
- `ms3_lambda-0.0.1-SNAPSHOT-aws.jar` -> **Use este arquivo para upload na AWS Lambda.**
- `ms3_lambda-0.0.1-SNAPSHOT.jar` -> JAR padrão do Spring Boot (execução standalone / EC2).

### 2. Configurar a função no Console da AWS Lambda

- **Runtime:** `Java 21`
- **Handler:** `org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest`
- **Variáveis de ambiente na Lambda:**
  - `SPRING_CLOUD_FUNCTION_DEFINITION` = `validarPedido`
  - `TRACING_ENABLED` = `false` *(ou aponte `ZIPKIN_ENDPOINT` para uma URL acessível pela Lambda)*
- **Function URL:** crie uma Function URL (Auth Type: `NONE` para testes acadêmicos ou configure IAM/API Gateway) e copie a URL gerada (ex.: `https://<id>.lambda-url.us-east-1.on.aws/`) para configurar `LAMBDA_URL` no `ms1-orquestrator`.

> O cliente HTTP do `ms1-orquestrator` envia `POST` diretamente na URL base definida em `LAMBDA_URL`, compatível tanto com `/validarPedido` local quanto com a raiz `/` de uma Function URL da AWS Lambda.

---

## Modo 2: Execução Local ou EC2 Standalone

Executa localmente na porta `8083`, respondendo em `POST http://localhost:8083/validarPedido`:

```bash
mvn -f ms3-lambda/ spring-boot:run
```
