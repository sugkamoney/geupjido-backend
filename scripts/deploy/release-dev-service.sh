#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo '사용법: sudo bash release-dev-service.sh deploy JAR_PATH | rollback' >&2
  exit 2
}

if [[ ${EUID} -ne 0 ]]; then
  echo 'root 권한으로 실행해야 합니다.' >&2
  exit 1
fi

mode=${1:-}
case "$mode" in
  deploy)
    [[ $# -eq 2 && -f $2 ]] || usage
    source_jar=$2
    ;;
  rollback)
    [[ $# -eq 1 ]] || usage
    ;;
  *) usage ;;
esac

app_dir=/opt/geupjido
release_dir=$app_dir/releases
current_link=$app_dir/geupjido-backend.jar
previous_link=$app_dir/previous.jar
service=geupjido-dev.service
health_url=http://127.0.0.1:8080/actuator/health
health_attempts=${GEUPJIDO_HEALTH_ATTEMPTS:-30}
[[ $health_attempts =~ ^[1-9][0-9]*$ ]] || { echo '헬스 체크 횟수가 올바르지 않습니다.' >&2; exit 1; }

install -d -o root -g root -m 0755 "$release_dir"
exec 9>/run/lock/geupjido-dev-release.lock
if ! flock -n 9; then
  echo '다른 배포 또는 롤백이 진행 중입니다.' >&2
  exit 1
fi

atomic_link() {
  local target=$1
  local link=$2
  local temporary_link="${link}.next.$$"
  ln -s "$target" "$temporary_link"
  mv -Tf "$temporary_link" "$link"
}

wait_for_health() {
  local max_attempts=${1:-$health_attempts}
  local attempt
  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if curl --fail --silent --max-time 2 "$health_url" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if [[ -L $current_link ]]; then
  old_release=$(readlink -f "$current_link")
elif [[ -f $current_link ]]; then
  old_hash=$(sha256sum "$current_link" | cut -d ' ' -f 1)
  old_release="$release_dir/initial-${old_hash:0:12}.jar"
  if [[ ! -f $old_release ]]; then
    install -o root -g root -m 0644 "$current_link" "$old_release"
  fi
else
  echo '현재 실행 중인 JAR를 찾을 수 없습니다.' >&2
  exit 1
fi
[[ -f $old_release ]] || { echo '이전 JAR 파일이 없습니다.' >&2; exit 1; }

if [[ $mode == deploy ]]; then
  new_hash=$(sha256sum "$source_jar" | cut -d ' ' -f 1)
  release_name="$(date -u +%Y%m%dT%H%M%SZ)-${new_hash:0:12}-$$.jar"
  new_release="$release_dir/$release_name"
  install -o root -g root -m 0644 "$source_jar" "$new_release"
else
  [[ -L $previous_link ]] || { echo '되돌릴 이전 버전이 없습니다.' >&2; exit 1; }
  new_release=$(readlink -f "$previous_link")
  case "$new_release" in
    "$release_dir"/*) ;;
    *) echo '이전 버전 경로가 올바르지 않습니다.' >&2; exit 1 ;;
  esac
  [[ -f $new_release ]] || { echo '이전 버전 파일이 없습니다.' >&2; exit 1; }
fi

switched=0
restore_on_failure() {
  local status=$?
  trap - EXIT
  if [[ $status -ne 0 && $switched -eq 1 ]]; then
    echo '새 버전 확인 실패: 이전 JAR로 되돌립니다.' >&2
    atomic_link "$old_release" "$current_link" || true
    systemctl restart "$service" || true
    if ! wait_for_health 30; then
      echo '이전 버전의 상태도 확인되지 않았습니다. 서비스 로그를 확인하세요.' >&2
    else
      echo '이전 버전의 상태가 정상으로 복구됐습니다.' >&2
    fi
  fi
  exit "$status"
}
trap restore_on_failure EXIT

atomic_link "$new_release" "$current_link"
switched=1
systemctl restart "$service"
wait_for_health
atomic_link "$old_release" "$previous_link"
switched=0
echo "${mode} 완료: $new_release"
