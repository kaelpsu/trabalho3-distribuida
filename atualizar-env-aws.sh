#!/usr/bin/env bash
set -euo pipefail

# === Configuração fixa (preencha uma única vez com os IPs privados da sua VPC) ===
KEY_PEM="$HOME/Documentos/smartshop-key.pem"
GW_IP="3.16.175.71"
LAMBDA_URL="https://gddiuasxw3losom6dnkjlk775a0toocu.lambda-url.us-east-2.on.aws/"
VAULT_TOKEN="root-token-trabalho3-apresentacao"
ENV_FILE="${1:-.env}"

OBS_PRIV="10.30.1.10"
EUREKA1_PRIV="10.30.1.11"
EUREKA2_PRIV="10.30.1.12"
CONFIG_PRIV="10.30.1.13"
MS1_PRIV="10.30.1.15"
MS2_PRIV="10.30.1.16"

# === Opções SSH com ProxyCommand (repassa -i para o salto no Gateway e para o destino) ===
SSH_BASE_OPTS=(
  -i "$KEY_PEM"
  -o IdentitiesOnly=yes
  -o BatchMode=yes
  -o StrictHostKeyChecking=no
  -o UserKnownHostsFile=/dev/null
  -o LogLevel=ERROR
  -o ConnectTimeout=6
)

PROXY_CMD="ssh -i \"$KEY_PEM\" -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=6 -W %h:%p ec2-user@${GW_IP}"

get_public_ip() {
  local priv_ip="$1"
  ssh "${SSH_BASE_OPTS[@]}" -o ProxyCommand="$PROXY_CMD" "ec2-user@${priv_ip}" \
    'TOKEN=$(curl -sS --max-time 3 -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60") && curl -sS --max-time 3 -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4' \
    2>/dev/null || true
}

echo "Consultando IPs públicos via Bastion ($GW_IP)..."

OBS_IP=$(get_public_ip "$OBS_PRIV")
EUREKA1_IP=$(get_public_ip "$EUREKA1_PRIV")
EUREKA2_IP=$(get_public_ip "$EUREKA2_PRIV")
CONFIG_IP=$(get_public_ip "$CONFIG_PRIV")
MS1_IP=$(get_public_ip "$MS1_PRIV")
MS2_IP=$(get_public_ip "$MS2_PRIV")

cat << EOF > "$ENV_FILE"
export KEY_PEM=~/Documentos/smartshop-key.pem
export GW_IP="${GW_IP}"
export OBS_IP="${OBS_IP:-<NAO_ENCONTRADO>}"
export EUREKA1_IP="${EUREKA1_IP:-<NAO_ENCONTRADO>}"
export EUREKA2_IP="${EUREKA2_IP:-<NAO_ENCONTRADO>}"
export CONFIG_IP="${CONFIG_IP:-<NAO_ENCONTRADO>}"
export MS1_IP="${MS1_IP:-<NAO_ENCONTRADO>}"
export MS2_IP="${MS2_IP:-<NAO_ENCONTRADO>}"
export LAMBDA_URL="${LAMBDA_URL}"
export VAULT_TOKEN="${VAULT_TOKEN}"
EOF

cat "$ENV_FILE"