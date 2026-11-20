#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 || $# -ne 3 || ! $2 =~ ^[0-9]{5}$ || ! $3 =~ ^[0-9]{4}(0[1-9]|1[0-2])$ ]]; then
  echo '사용법: sudo bash run-dev-weekly-trade-smoke.sh JAR_PATH DISTRICT_CODE YYYYMM' >&2
  exit 2
fi

jar=$1
district=$2
month=$3
env_file=/etc/geupjido/dev.env
service=geupjido-dev.service
mapping=/opt/geupjido/data/location/mapping/location-mapping.json
reference=/opt/geupjido/data/location/reference/legal-dongs-20260917.tsv

[[ -s $jar && -r $env_file && -s $mapping && -s $reference ]] || {
  echo 'JAR, 비밀 환경 파일 또는 실거래가 입력 파일이 없습니다.' >&2
  exit 1
}
systemctl is-active --quiet "$service" || {
  echo '개발 API 서비스가 실행 중이 아닙니다.' >&2
  exit 1
}

# systemd EnvironmentFile은 셸 스크립트가 아니므로 source/eval로 실행하지 않는다.
db_url=
db_user=
db_password=
api_key=
env_value() {
  local value=${1%$'\r'}
  if (( ${#value} >= 2 )) && \
      { [[ ${value:0:1} == '"' && ${value: -1} == '"' ]] || \
        [[ ${value:0:1} == "'" && ${value: -1} == "'" ]]; }; then
    value=${value:1:${#value}-2}
  fi
  printf '%s' "$value"
}
while IFS= read -r line; do
  case "$line" in
    DB_URL=*) db_url=$(env_value "${line#*=}") ;;
    DB_USERNAME=*) db_user=$(env_value "${line#*=}") ;;
    DB_PASSWORD=*) db_password=$(env_value "${line#*=}") ;;
    DATA_GO_KR_SERVICE_KEY=*) api_key=$(env_value "${line#*=}") ;;
  esac
done < "$env_file"
[[ $db_url == jdbc:postgresql://* && -n $db_user && -n $db_password && -n $api_key ]] || {
  echo 'DB 연결 또는 공공데이터포털 인증 설정이 준비되지 않았습니다.' >&2
  exit 1
}
db_conn=${db_url#jdbc:}

snapshot() {
  local sql
  sql="SELECT \
    (SELECT count(*) FROM apartment_trade WHERE district_code = '$district' AND to_char(deal_date, 'YYYYMM') = '$month'), \
    (SELECT count(*) FROM apartment_trade WHERE district_code = '$district' AND to_char(deal_date, 'YYYYMM') = '$month' AND complex_id IS NOT NULL), \
    (SELECT count(*) FROM complex WHERE price IS NOT NULL), \
    (SELECT coalesce(sum(price), 0) FROM complex WHERE price IS NOT NULL)"
  PGPASSWORD="$db_password" psql -U "$db_user" -Atc "$sql" "$db_conn"
}

install -d -o root -g root -m 0700 /var/backups/geupjido
backup="/var/backups/geupjido/trade-smoke-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
umask 077
before=$(snapshot)
echo "검증 전: 거래|연결 거래|가격 단지|가격 합계 = $before"
PGPASSWORD="$db_password" pg_dump -Fc -f "$backup" -U "$db_user" "$db_conn"
unset api_key
pg_restore -l "$backup" >/dev/null
echo "검증 전 DB 백업 완료: $backup"

install -d -o root -g root -m 0755 /opt/geupjido/releases
smoke_jar=$(mktemp /opt/geupjido/releases/trade-smoke.XXXXXX.jar)
trap 'rm -f "$smoke_jar"' EXIT
install -o root -g root -m 0644 "$jar" "$smoke_jar"
app_stopped=0
restore_api() {
  local status=$?
  trap - EXIT
  if [[ $app_stopped -eq 1 ]]; then
    if ! systemctl start "$service"; then
      echo '개발 API 서비스 재시작에 실패했습니다.' >&2
      status=1
    else
      local attempt
      local healthy=0
      for ((attempt = 1; attempt <= 30; attempt++)); do
        if curl --fail --silent --max-time 2 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
          healthy=1
          break
        fi
        sleep 2
      done
      if [[ $healthy -eq 0 ]]; then
        echo '개발 API 재시작 후 health가 UP이 아닙니다.' >&2
        status=1
      fi
    fi
  fi
  rm -f "$smoke_jar"
  exit "$status"
}
trap restore_api EXIT
app_stopped=1
systemctl stop "$service"

set +e
systemd-run --pipe --wait --collect \
  --unit="geupjido-weekly-trade-smoke-$(date -u +%Y%m%dT%H%M%SZ)" \
  -p User=geupjido -p Group=geupjido \
  -p WorkingDirectory=/opt/geupjido \
  -p EnvironmentFile=/etc/geupjido/dev.env \
  -p NoNewPrivileges=yes -p ProtectSystem=full -p ProtectHome=yes \
  /usr/bin/java -Xms128m -Xmx448m -jar "$smoke_jar" \
  --spring.profiles.active=dev \
  --spring.main.web-application-type=none \
  --app.external.apartment-trade.enabled=true \
  --app.weekly-trade-refresh.run-on-startup=false \
  --app.weekly-trade-refresh.schedule-enabled=false \
  --app.weekly-trade-refresh.smoke-run-on-startup=true \
  --app.weekly-trade-refresh.smoke-district-code="$district" \
  --app.weekly-trade-refresh.smoke-deal-year-month="$month" \
  --app.worldcup-rating.schedule-enabled=false \
  --app.weekly-report.schedule-enabled=false
smoke_status=$?
set -e
after=$(snapshot)
echo "검증 후: 거래|연결 거래|가격 단지|가격 합계 = $after"
exit "$smoke_status"
