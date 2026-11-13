#!/usr/bin/env bash
set -euo pipefail

jar_path=${1:?Usage: install-dev-service.sh JAR_PATH RDS_CA_PATH}
ca_path=${2:?Usage: install-dev-service.sh JAR_PATH RDS_CA_PATH}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

if ! id geupjido >/dev/null 2>&1; then
  sudo useradd --system --home-dir /opt/geupjido --shell /sbin/nologin geupjido
fi

sudo install -d -o root -g root -m 0755 /opt/geupjido
sudo install -d -o root -g geupjido -m 0750 /etc/geupjido
sudo install -o root -g root -m 0644 "$jar_path" /opt/geupjido/geupjido-backend.jar
sudo install -o root -g geupjido -m 0640 "$ca_path" /etc/geupjido/rds-global-bundle.pem
sudo install -o root -g root -m 0644 "$script_dir/geupjido-dev.service" /etc/systemd/system/geupjido-dev.service
sudo systemctl daemon-reload

echo '서비스 파일을 설치했습니다. /etc/geupjido/dev.env 설정 후 서비스를 시작하세요.'
