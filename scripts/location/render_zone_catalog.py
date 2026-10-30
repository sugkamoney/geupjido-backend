"""서울·경기 권역 결정표로 읽기 쉬운 Markdown 기준표를 생성한다."""

from __future__ import annotations

import csv
from collections import defaultdict
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SEOUL = ROOT / "data/location/mapping/seoul-zone-decisions.tsv"
GYEONGGI = ROOT / "data/location/mapping/gyeonggi-zone-decisions.tsv"
OUTPUT = ROOT / "docs/architecture/location-zone-catalog.md"


def read_rows(path: Path, expected_count: int) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    if len(rows) != expected_count:
        raise ValueError(f"{path.name}: 예상 {expected_count}개, 실제 {len(rows)}개")
    return rows


def markdown_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ").strip()


def section(
    title: str,
    rows: list[dict[str, str]],
    group_key: str,
    zone_key: str,
    area_key: str,
    tier_key: str,
) -> list[str]:
    grouped: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        grouped[row[group_key]].append(row)

    lines = [f"## {title} · {len(rows)}개 권역", ""]
    for group in sorted(grouped):
        zones = sorted(
            grouped[group],
            key=lambda row: (Decimal(row[tier_key].split()[0]), row[zone_key]),
        )
        lines.extend([
            f"### {group} · {len(zones)}개", "",
            "| 권역 | 포함 법정동·리 또는 분할 범위 | 초기 급지 |",
            "|---|---|---:|",
        ])
        for row in zones:
            lines.append(
                f"| {markdown_cell(row[zone_key])} "
                f"| {markdown_cell(row[area_key])} "
                f"| {markdown_cell(row[tier_key])} |"
            )
        lines.append("")
    return lines


def render() -> str:
    seoul = read_rows(SEOUL, 150)
    gyeonggi = read_rows(GYEONGGI, 132)
    lines = [
        "# 서울·경기 권역 기준표",
        "",
        "> 2026-10-30, 이슈 #101의 로컬 PostGIS 적재 기준. 서울 150개, 경기 132개, 총 282개 권역.",
        "",
        "이 문서는 권역별 이름, 포함 지역, 초기 급지를 한눈에 확인하기 위한 표다. "
        "편집 원본은 [서울 결정표](../../data/location/mapping/seoul-zone-decisions.tsv)와 "
        "[경기 결정표](../../data/location/mapping/gyeonggi-zone-decisions.tsv)다. "
        "원본을 수정한 뒤 `python3 -B scripts/location/render_zone_catalog.py`로 이 문서를 다시 생성한다.",
        "",
        "- **급지**: 숫자는 초기 배치값이며 사용자 투표 점수, 단지 가격, 권역 내 순위가 아니다. "
        "작을수록 높은 초기 급지를 뜻한다. 서울의 괄호 안 범위는 병합 전 급지의 참고 범위이고, "
        "실제 초기값은 괄호 앞 숫자다. 새 경기 권역의 급지는 거래 표본과 인접 권역을 참고한 임시 편집값이다.",
        "- **범위**: 일반 권역은 표의 법정동·리 경계를 쓴다. `일부`, `경계 밖`, "
        "`단지`처럼 적힌 권역은 법정동 코드만으로 판단하지 않고 아래의 별도 경계 규칙을 적용한다. "
        "정확한 코드와 도형은 지역 매핑과 PostGIS 적재 결과를 기준으로 확인한다.",
        "- **적재 상태**: 로컬 DB에서 서울 단지 3,410개와 경기 단지 2,903개가 "
        "각각 하나의 권역에 배정됐다. 이 문서는 인천 권역을 다루지 않는다. "
        "운영 DB 반영이나 PR CI 검증을 뜻하지 않는다.",
        "",
        "## 법정동을 나누는 권역",
        "",
        "| 지역 | 분할 기준 |",
        "|---|---|",
        "| 서울 용산구 이촌동 | 동부이촌은 이촌1동, 서부이촌은 이촌2동 행정경계를 법정동 경계와 교차해 구분 |",
        "| 서울 양천구 목동·신정동 | 목동 단지는 목동신시가지 1~14단지의 필지 14개만 포함. 나머지 목동·신정 권역에서는 해당 필지를 제외 |",
        "| 경기 하남시 감이동 | 위례 행정경계 안은 위례, 밖은 감이 권역 |",
        "| 경기 안양시 관양동 | 인덕원 행정경계 안은 인덕원, 밖은 관양 권역 |",
        "| 경기 남양주시 다산동 | 공식 다산진건·다산지금 지구 경계 안을 각각 배정하고, 두 경계 밖은 다산기타 권역 |",
        "| 경기 기타 부분 생활권 | 미사·목감·운정·금촌 등은 결정표의 분할 기준과 단지 좌표·행정경계를 함께 적용 |",
        "",
        "서울의 상세 결정은 [서울 확대 기록](../history/2026-10-29-seoul-zone-expansion.md), "
        "경기의 상세 결정과 제외 지역은 [경기 확대 기록](../history/2026-10-29-gyeonggi-zone-expansion.md)에 남겼다. "
        "오산시, 김포 고촌읍, 파주 문산읍, 남양주 진접읍·화도읍은 이 기준에서 제외한다.",
        "",
    ]
    lines.extend(section("서울", seoul, "시군구", "권역", "해당 지역", "급지"))
    lines.extend(section(
        "경기", gyeonggi, "시군구", "권역명", "포함_법정동_또는_리", "초기급지"
    ))
    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(), encoding="utf-8")
    print(f"권역 기준표 생성: {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
