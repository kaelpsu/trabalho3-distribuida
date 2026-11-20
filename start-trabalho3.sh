#!/usr/bin/env bash

set -euo pipefail

nova_aba() {
    local titulo="$1"
    local comando="$2"

    qterminal \
        --workdir "$PWD" \
        --execute bash -lc \
        "echo '=== $titulo ==='; $comando; status=\$?; echo; echo 'Processo finalizado (código: '\$status')'; exec bash" &
}

# 1. Eureka e infraestrutura Spring
nova_aba "Eureka Peer 1" \
    "mvn -f eureka-server/ spring-boot:run -Dspring-boot.run.profiles=peer1"

nova_aba "Eureka Peer 2" \
    "mvn -f eureka-server/ spring-boot:run -Dspring-boot.run.profiles=peer2"

nova_aba "Config Server" \
    "mvn -f config-server/ spring-boot:run"

# 2. Docker
echo "Subindo containers Docker..."
docker compose up -d

echo "Aguardando Vault..."
sleep 3

# Configuração do Vault
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' \
  -e VAULT_TOKEN='root-token-trabalho3' \
  vault-trabalho3 \
  vault kv put secret/ms2-ai-service \
  gemini.api-key="SUA-CHAVE-API" \
  gemini.model="gemini-3.5-flash-lite"

# 3. Microsserviços
nova_aba "API Gateway" \
    "mvn -f api-gateway/ spring-boot:run"

nova_aba "MS1 Orquestrador" \
    "mvn -f ms1-orquestrator/ spring-boot:run"

nova_aba "MS2 AI Service" \
    "mvn -f ms2-ai-service/ spring-boot:run"

nova_aba "MS3 Lambda" \
    "mvn -f ms3-lambda/ spring-boot:run"

echo "Inicialização dos serviços solicitada."
