#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 || $# -ne 1 ]]; then
  echo '사용법: sudo bash install-dev-weekly-trade-timer.sh install|enable' >&2
  exit 2
fi

mode=$1
case "$mode" in
  install|enable) ;;
  *)
    echo '사용법: sudo bash install-dev-weekly-trade-timer.sh install|enable' >&2
    exit 2
    ;;
esac
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
service=geupjido-dev-weekly-trade.service
timer=geupjido-dev-weekly-trade.timer

[[ -s /opt/geupjido/geupjido-backend.jar ]] || {
  echo '배포된 개발 API JAR가 없습니다.' >&2
  exit 1
}
install -d -o root -g root -m 0755 /usr/local/libexec/geupjido
install -o root -g root -m 0755 \
  "$script_dir/run-dev-weekly-trade-full.sh" \
  /usr/local/libexec/geupjido/run-dev-weekly-trade-full.sh
install -o root -g root -m 0644 "$script_dir/$service" "/etc/systemd/system/$service"
install -o root -g root -m 0644 "$script_dir/$timer" "/etc/systemd/system/$timer"
systemctl daemon-reload

if [[ $mode == enable ]]; then
  systemctl enable --now "$timer"
  systemctl list-timers --all "$timer"
else
  echo '타이머 파일을 설치했습니다. 예약 실행은 아직 활성화하지 않았습니다.'
fi
