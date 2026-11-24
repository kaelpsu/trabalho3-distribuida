# Implantação na AWS e CI/CD
---

## Visão Geral da Arquitetura Provisionada
| Serviço / Instância | Tipo EC2 | IP Privado (fixo) | IP Público | Porta(s) | Execução |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Observabilidade + Vault** (`prometheus`, `grafana`, `zipkin`, `vault`) | `t3.small` (2 GB RAM + 2 GB swap) | `10.30.1.10` | Dinâmico | `3000`, `9090`, `9411`, `8200` | Docker Compose |
| **Eureka Peer 1** | `t3.micro` (1 GB RAM + 1 GB swap) | `10.30.1.11` | Dinâmico | `8761` | JAR (`app.jar`) |
| **Eureka Peer 2** | `t3.micro` (1 GB RAM + 1 GB swap) | `10.30.1.12` | Dinâmico | `8762` | JAR (`app.jar`) |
| **Config Server** (`git`) | `t3.micro` (1 GB RAM + 1 GB swap) | `10.30.1.13` | Dinâmico | `8888` | JAR (`app.jar`) |
| **API Gateway + Bastion Host** | `t3.micro` (1 GB RAM + 2 GB swap) | `10.30.1.14` | **Elastic IP (fixo)** | `8080`, `22` (SSH) | JAR (`app.jar`) |
| **MS1 Orquestrator** | `t3.micro` (1 GB RAM + 2 GB swap) | `10.30.1.15` | Dinâmico | `8081` | JAR (`app.jar`) |
| **MS2 AI Service** | `t3.micro` (1 GB RAM + 2 GB swap) | `10.30.1.16` | Dinâmico | `8082` | JAR (`app.jar`) |
| **MS3 Lambda** (`validarPedido`) | AWS Lambda | Gerenciado AWS | Function URL HTTPS | `/` | Shaded JAR via S3 |

### **Boot automático e tolerância a reinicializações (`per-boot`)**
   - Em toda inicialização, `/var/lib/cloud/scripts/per-boot/start-smartshop.sh` sobe o serviço usando `/home/ec2-user/app/smartshop/.env`.
   - Na instância de observabilidade, o script também aguarda o Vault subir e regrava automaticamente `secret/ms2-ai-service`.
   - No `api-gateway`, `ms1-orquestrator` e `ms2-ai-service`, o script aguarda `config-server` (`10.30.1.13:8888`) e `vault` (`10.30.1.10:8200`) ficarem saudáveis antes de iniciar a JVM.

---
## Remoção Completa da Infraestrutura (Teardown)

Quando finalizar a disciplina e quiser remover tudo para evitar qualquer cobrança:

1. **Esvazie o Bucket S3** (`DeployArtifactsBucketName`), pois o CloudFormation não apaga buckets que contenham objetos ou versões:
   - Console S3 -> selecione o bucket -> **Empty** *(lembre-se de incluir versões anteriores se o versionamento estiver habilitado)*, ou via CLI:
     ```bash
     aws s3 rm "s3://<BUCKET_NAME>" --recursive
     ```
     *(Se houver versões antigas no bucket versionado, use o botão **Empty** no Console S3.)*
2. Acesse **CloudFormation -> Stacks -> `smartshop-trabalho3` -> Delete** e aguarde **`DELETE_COMPLETE`**.
