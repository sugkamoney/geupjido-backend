#!/usr/bin/env python3
"""Compare unpriced complexes with the current official basic-info API.

The report contains only public address fields. API credentials are never logged.
"""

import argparse
import csv
import json
import os
from pathlib import Path
import time
from urllib.parse import unquote, urlencode
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

from audit_unpriced_complexes import ROOT


INPUT = ROOT / "data/trade/audit-runs/unpriced-complexes.csv"
OUTPUT = ROOT / "data/trade/audit-runs/unpriced-official-basic.csv"


def api_settings():
    settings = {}
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            key, value = line.split("=", 1)
            settings[key.strip()] = value.strip().strip("\"'")
    key = os.getenv("DATA_GO_KR_SERVICE_KEY") or settings.get("DATA_GO_KR_SERVICE_KEY")
    base = (os.getenv("DATA_GO_KR_APT_BASIC_INFO_BASE_URL")
            or settings.get("DATA_GO_KR_APT_BASIC_INFO_BASE_URL"))
    if not key or not base:
        raise ValueError("공동주택 기본정보 API 설정이 없습니다")
    return unquote(key), base.rstrip("/")


def fetch(complex_id, service_key, base_url):
    query = urlencode({"serviceKey": service_key, "kaptCode": complex_id})
    request = Request(f"{base_url}/getAphusBassInfoV5?{query}",
                      headers={"Accept": "application/xml"})
    with urlopen(request, timeout=20) as response:
        body = response.read()
    if body.lstrip().startswith(b"{"):
        payload = json.loads(body)
        result = payload.get("response", {})
        header = result.get("header", {})
        code = header.get("resultCode")
        if code != "00":
            return {"status": f"API_CODE_{code or 'UNKNOWN'}"}
        item = result.get("body", {}).get("item")
        if not isinstance(item, dict):
            return {"status": "NO_ITEM"}
        return {"status": "OK", "official_name": item.get("kaptName", ""),
                "official_jibun": item.get("kaptAddr", ""),
                "official_road": item.get("doroJuso", ""),
                "official_legal_dong_code": item.get("bjdCode", "")}
    root = ET.fromstring(body)
    code = root.findtext("./header/resultCode") or root.findtext(".//returnReasonCode")
    if code != "00":
        return {"status": f"API_CODE_{code or 'UNKNOWN'}"}
    item = root.find("./body/item")
    if item is None:
        return {"status": "NO_ITEM"}
    return {"status": "OK", "official_name": item.findtext("kaptName") or "",
            "official_jibun": item.findtext("kaptAddr") or "",
            "official_road": item.findtext("doroJuso") or "",
            "official_legal_dong_code": item.findtext("bjdCode") or ""}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ids", nargs="*", default=[])
    parser.add_argument("--interval", type=float, default=0.3)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    if args.interval < 0.2:
        parser.error("--interval must be at least 0.2 seconds")
    with INPUT.open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))
    ids = set(args.ids)
    if ids:
        rows = [row for row in rows if row["complex_id"] in ids]
        if len(rows) != len(ids):
            parser.error("--ids contains an ID outside the current gap report")
    key, base = api_settings()
    columns = ["complex_id", "db_name", "db_jibun", "db_road", "status",
               "official_name", "official_jibun", "official_road",
               "official_legal_dong_code"]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=columns)
        writer.writeheader()
        for index, row in enumerate(rows):
            if index:
                time.sleep(args.interval)
            try:
                result = fetch(row["complex_id"], key, base)
            except HTTPError as error:
                result = {"status": f"HTTP_{error.code}"}
            except URLError as error:
                result = {"status": f"NETWORK_{type(error.reason).__name__}"}
            except (OSError, ET.ParseError) as error:
                result = {"status": f"REQUEST_{type(error).__name__}"}
            writer.writerow({"complex_id": row["complex_id"],
                             "db_name": row["name"],
                             "db_jibun": row["address_jibun"],
                             "db_road": row["address_road"], **result})
            file.flush()
    print(f"공식 기본정보 대조: {len(rows)}건, 결과: {args.output}")


if __name__ == "__main__":
    main()
