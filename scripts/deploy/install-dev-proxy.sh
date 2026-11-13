#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
version=2.11.9
archive="caddy_${version}_linux_arm64.tar.gz"
expected_sha512=9e5e76d7888542924de2c25353ae87c5f77e2a22c5b1198633cae13688922282e05383d142599205c88d8d80eee5e967d291214d7abce4835329a191b20dd6e0
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

curl --fail --location --silent --show-error --retry 3 --output "$temp_dir/$archive" \
  "https://github.com/caddyserver/caddy/releases/download/v${version}/${archive}"
printf '%s  %s\n' "$expected_sha512" "$temp_dir/$archive" | sha512sum --check --status -
tar -xzf "$temp_dir/$archive" -C "$temp_dir" caddy

if ! id caddy >/dev/null 2>&1; then
  sudo useradd --system --home-dir /var/lib/caddy --shell /sbin/nologin caddy
fi

sudo install -d -o root -g root -m 0755 /etc/caddy
sudo install -o root -g root -m 0755 "$temp_dir/caddy" /usr/local/bin/caddy
sudo install -o root -g root -m 0644 "$script_dir/Caddyfile" /etc/caddy/Caddyfile
sudo install -o root -g root -m 0644 "$script_dir/geupjido-dev-proxy.service" /etc/systemd/system/geupjido-dev-proxy.service
sudo /usr/local/bin/caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl daemon-reload
sudo systemctl enable --now geupjido-dev-proxy.service

echo 'HTTPS 프록시를 설치하고 시작했습니다.'
