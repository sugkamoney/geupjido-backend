"""행정안전부 법정동 코드 원본에서 수도권 읍면동 참조 파일을 만든다."""

import csv
import json
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATH = PROJECT_ROOT / "data/location/source/legal-dong-codes-20260917.txt"
MAPPING_PATH = PROJECT_ROOT / "data/location/mapping/location-mapping.json"
OUTPUT_PATH = PROJECT_ROOT / "data/location/reference/legal-dongs-20260917.tsv"
CAPITAL_REGION_PREFIXES = ("11", "28", "41")


def build_reference() -> None:
    with SOURCE_PATH.open(encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames != ["법정동코드", "법정동명", "폐지여부"]:
            raise ValueError("법정동 코드 원본의 열 구성이 예상과 다릅니다.")

        rows = []
        seen_codes = set()
        seen_district_names = set()

        for row in reader:
            code = row["법정동코드"].strip()
            full_name = row["법정동명"].strip()

            if row["폐지여부"].strip() != "존재":
                continue
            if not code.startswith(CAPITAL_REGION_PREFIXES):
                continue
            if not code.endswith("00") or code.endswith("00000"):
                continue

            if len(code) != 10 or not code.isascii() or not code.isdigit():
                raise ValueError(f"잘못된 법정동 코드입니다: {code}")

            name_parts = full_name.split()
            if len(name_parts) < 3:
                raise ValueError(f"읍면동 이름을 확인할 수 없습니다: {full_name}")

            district_name_key = (code[:5], name_parts[-1])
            if code in seen_codes or district_name_key in seen_district_names:
                raise ValueError(f"중복된 법정동 코드 또는 시군구·동 이름: {code}")

            seen_codes.add(code)
            seen_district_names.add(district_name_key)
            rows.append((code, full_name))

    mapping = json.loads(MAPPING_PATH.read_text(encoding="utf-8"))
    mapped_codes = {
        code
        for zone in mapping["zones"]
        for code in zone["dongCodes"]
    }
    missing_codes = mapped_codes - seen_codes
    if missing_codes:
        raise ValueError(
            "권역 매핑의 법정동 코드가 참조 파일에 없습니다: "
            + ", ".join(sorted(missing_codes))
        )

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with OUTPUT_PATH.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t", lineterminator="\n")
        writer.writerow(("법정동코드", "법정동명"))
        writer.writerows(rows)

    print(
        f"법정동 참조 파일 생성: {len(rows)}개, "
        f"권역 매핑 코드 확인: {len(mapped_codes)}개"
    )


if __name__ == "__main__":
    build_reference()
