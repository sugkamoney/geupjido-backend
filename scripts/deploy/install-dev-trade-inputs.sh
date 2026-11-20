#!/usr/bin/env bash
set -euo pipefail

if [[ ${EUID} -ne 0 || $# -ne 2 || ! -s $1 || ! -s $2 ]]; then
  echo '사용법: sudo bash install-dev-trade-inputs.sh MAPPING_JSON LEGAL_DONGS_TSV' >&2
  exit 2
fi

mapping_dir=/opt/geupjido/data/location/mapping
reference_dir=/opt/geupjido/data/location/reference
report_dir=/var/lib/geupjido/trade
mapping_tmp="$mapping_dir/.location-mapping.json.$$"
reference_tmp="$reference_dir/.legal-dongs-20260917.tsv.$$"

cleanup() {
  rm -f "$mapping_tmp" "$reference_tmp"
}
trap cleanup EXIT

install -d -o root -g root -m 0755 "$mapping_dir" "$reference_dir"
install -d -o geupjido -g geupjido -m 0750 "$report_dir"
install -o root -g root -m 0644 "$1" "$mapping_tmp"
install -o root -g root -m 0644 "$2" "$reference_tmp"
mv -f "$mapping_tmp" "$mapping_dir/location-mapping.json"
mv -f "$reference_tmp" "$reference_dir/legal-dongs-20260917.tsv"

echo '개발 서버 실거래가 입력 파일 설치 완료'
