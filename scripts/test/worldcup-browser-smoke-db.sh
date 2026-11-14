#!/usr/bin/env bash
set -euo pipefail

container=geupjido-postgres
source_db=geupjido
smoke_db=${2:-geupjido_worldcup_browser_smoke}
case "$smoke_db" in
  geupjido_worldcup_browser_smoke|geupjido_worldcup_virtual_voters) ;;
  *)
    echo "허용되지 않은 검증 DB 이름입니다: $smoke_db" >&2
    exit 2
    ;;
esac
dump_file="/tmp/$smoke_db.dump"
toc_file="/tmp/$smoke_db.list"

case "${1:-}" in
  create)
    if docker exec -u postgres "$container" psql -d postgres -Atc \
      "SELECT 1 FROM pg_database WHERE datname = '$smoke_db'" | grep -qx 1; then
      echo "이미 검증 DB가 있습니다: $smoke_db" >&2
      exit 1
    fi
    docker exec -u postgres "$container" pg_dump -Fc -f "$dump_file" "$source_db"
    docker exec -u postgres "$container" sh -c \
      "pg_restore -l '$dump_file' | grep -v -e 'EXTENSION - pg_cron' -e 'EXTENSION pg_cron' -e 'TABLE DATA cron ' -e 'SEQUENCE SET cron ' > '$toc_file'"
    docker exec -u postgres "$container" createdb -O geupjido "$smoke_db"
    docker exec -u postgres "$container" pg_restore --exit-on-error --no-owner --no-acl -L "$toc_file" \
      -d "$smoke_db" "$dump_file"
    docker exec -u postgres "$container" psql -d "$smoke_db" -v ON_ERROR_STOP=1 -c \
      'GRANT ALL ON ALL TABLES IN SCHEMA public TO geupjido; GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO geupjido;'
    docker exec -u postgres "$container" rm -f "$dump_file"
    docker exec -u postgres "$container" rm -f "$toc_file"
    echo "검증 DB 생성 완료: $smoke_db"
    ;;
  drop)
    docker exec -u postgres "$container" dropdb --if-exists "$smoke_db"
    docker exec -u postgres "$container" rm -f "$dump_file"
    docker exec -u postgres "$container" rm -f "$toc_file"
    echo "검증 DB 정리 완료: $smoke_db"
    ;;
  *)
    echo "사용법: $0 create|drop [geupjido_worldcup_browser_smoke|geupjido_worldcup_virtual_voters]" >&2
    exit 2
    ;;
esac
