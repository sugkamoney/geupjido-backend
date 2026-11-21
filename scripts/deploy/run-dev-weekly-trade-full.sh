#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 || $# -ne 2 || ! $2 =~ ^[1-9][0-9]*$ ]]; then
  echo '사용법: sudo bash run-dev-weekly-trade-full.sh JAR_PATH MONTHS_TO_COLLECT' >&2
  exit 2
fi

jar=$1
months=$2
env_file=/etc/geupjido/dev.env
service=geupjido-dev.service
mapping=/opt/geupjido/data/location/mapping/location-mapping.json
reference=/opt/geupjido/data/location/reference/legal-dongs-20260917.tsv

# 배포·롤백과 갱신이 같은 서비스와 JAR를 바꾸지 않도록 잠금을 공유한다.
exec 9>/run/lock/geupjido-dev-release.lock
if ! flock -n 9; then
  echo '다른 배포·롤백·주간 갱신이 진행 중입니다.' >&2
  exit 1
fi

[[ -s $jar && -r $env_file && -s $mapping && -s $reference ]] || {
  echo 'JAR, 비밀 환경 파일 또는 실거래가 입력 파일이 없습니다.' >&2
  exit 1
}
systemctl is-active --quiet "$service" || {
  echo '개발 API 서비스가 실행 중이 아닙니다.' >&2
  exit 1
}

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
  PGPASSWORD="$db_password" psql -U "$db_user" -Atc \
    'SELECT (SELECT count(*) FROM apartment_trade),
            (SELECT count(*) FROM apartment_trade WHERE complex_id IS NOT NULL),
            (SELECT count(*) FROM apartment_trade_conflict),
            (SELECT count(*) FROM complex WHERE price IS NOT NULL),
            (SELECT coalesce(sum(price), 0) FROM complex WHERE price IS NOT NULL)' \
    "$db_conn"
}

install -d -o root -g root -m 0700 /var/backups/geupjido
install -d -o geupjido -g geupjido -m 0750 /var/lib/geupjido/trade
install -d -o root -g root -m 0755 /opt/geupjido/releases
expected=$(python3 -c 'import json,sys; mapping=json.load(open(sys.argv[1])); print(len({zone["cityId"] for zone in mapping["zones"]}) * int(sys.argv[2]))' "$mapping" "$months")
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="/var/backups/geupjido/trade-full-$stamp-$$.dump"
log="/var/backups/geupjido/trade-full-$stamp-$$.log"
report="/var/lib/geupjido/trade/weekly-full-$stamp-$$.tsv"
umask 077
before=$(snapshot)
echo "검증 전 거래|연결|충돌|가격 단지|가격 합계: $before"
PGPASSWORD="$db_password" pg_dump -Fc -f "$backup" -U "$db_user" "$db_conn"
pg_restore -l "$backup" >/dev/null
unset api_key
echo "검증 전 DB 백업 완료: $backup"

smoke_jar=$(mktemp /opt/geupjido/releases/trade-full.XXXXXX.jar)
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
      local attempt healthy=0
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
  --unit="geupjido-weekly-trade-full-$stamp" \
  -p User=geupjido -p Group=geupjido \
  -p WorkingDirectory=/opt/geupjido \
  -p EnvironmentFile=/etc/geupjido/dev.env \
  -p RuntimeMaxSec=5min \
  -p NoNewPrivileges=yes -p ProtectSystem=full -p ProtectHome=yes \
  /usr/bin/java -Xms128m -Xmx448m -jar "$smoke_jar" \
  --spring.profiles.active=dev \
  --spring.main.web-application-type=none \
  --app.external.apartment-trade.enabled=true \
  --app.weekly-trade-refresh.run-on-startup=true \
  --app.weekly-trade-refresh.schedule-enabled=false \
  --app.weekly-trade-refresh.smoke-run-on-startup=false \
  --app.weekly-trade-refresh.months-to-collect="$months" \
  --app.weekly-trade-refresh.report-path="$report" \
  --app.worldcup-rating.schedule-enabled=false \
  --app.weekly-report.schedule-enabled=false >"$log" 2>&1
run_status=$?
set -e
after=$(snapshot)
echo "검증 후 거래|연결|충돌|가격 단지|가격 합계: $after"
echo "조합 결과: $report"
echo "실행 로그: $log"
if [[ -f $report ]]; then
  actual=$(awk 'END {if (NR > 0) print NR - 1; else print 0}' "$report")
  unique=$(tail -n +2 "$report" | cut -f2,3 | sort -u | wc -l)
  failed=$(awk -F '\t' 'NR > 1 && $4 == "FAILED" {count++} END {print count+0}' "$report")
  awk -F '\t' 'NR > 1 {count[$4]++; rows++} END {
    printf "처리 조합: %d, SUCCESS=%d, PARTIAL=%d, FAILED=%d\n",
      rows, count["SUCCESS"], count["PARTIAL"], count["FAILED"]
  }' "$report"
  if [[ $actual -ne $expected || $unique -ne $expected || $failed -ne 0 ]]; then
    echo "전체 대상 검증 실패: 기대=$expected, 행=$actual, 고유 조합=$unique, FAILED=$failed" >&2
    run_status=1
  fi
else
  echo '조합 결과 파일이 없습니다.' >&2
  run_status=1
fi
exit "$run_status"
