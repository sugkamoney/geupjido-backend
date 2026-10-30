import unittest
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from unittest.mock import patch

from link_detailed_unique_road import (
    candidate_for, decide_plan, load_db, months_with_stored_trades,
)


class UniqueRoadLinkTest(unittest.TestCase):
    def setUp(self):
        self.item = ET.fromstring(
            "<item><sggCd>11680</sggCd><umdCd>10100</umdCd>"
            "<umdNm>역삼동</umdNm>"
            "<roadNm>도곡로21길</roadNm><roadNmBonbun>0015</roadNmBonbun>"
            "<roadNmBubun>0000</roadNmBubun></item>"
        )
        self.row = {"district_code": "11680", "legal_dong_name": "역삼동",
                    "build_year": "2004", "raw_apartment_name": "역삼아파트"}
        self.candidate = {
            "id": "A1", "zone_id": "gangnam-yeoksam",
            "address_road": "서울특별시 강남구 도곡로21길 15",
            "approval_date": "2004-07-01", "sale_type": "분양",
            "name": "역삼아파트",
        }

    def test_unique_road_legal_code_and_year_match(self):
        candidate, status = candidate_for(
            self.item, self.row, {"1168010100": [self.candidate]}
        )
        self.assertEqual("MATCHED", status)
        self.assertEqual("A1", candidate["id"])

    def test_shared_road_is_not_auto_linked(self):
        candidate, status = candidate_for(
            self.item, self.row,
            {"1168010100": [self.candidate, dict(self.candidate, id="A2")]}
        )
        self.assertIsNone(candidate)
        self.assertEqual("SHARED_ROAD_ADDRESS", status)

    def test_wrong_build_year_with_same_name_is_linked(self):
        candidate, status = candidate_for(
            self.item, dict(self.row, raw_apartment_name="역삼아파트"),
            {"1168010100": [dict(self.candidate, name="역삼아파트",
                                    approval_date="2005-01-01")]}
        )
        self.assertEqual("A1", candidate["id"])
        self.assertEqual("MATCHED", status)

    def test_wrong_build_year_and_different_name_requires_review(self):
        candidate, status = candidate_for(
            self.item, self.row,
            {"1168010100": [dict(self.candidate, name="다른단지",
                                    approval_date="2005-01-01")]}
        )
        self.assertIsNone(candidate)
        self.assertEqual("NAME_REVIEW", status)

    def test_existing_apt_seq_conflict_is_not_auto_linked(self):
        stats = Counter()
        plan = decide_plan(
            {"1": [("111-1", self.candidate)]},
            defaultdict(set, {"111-1": {"A2"}}), stats,
        )
        self.assertEqual([], plan)
        self.assertEqual(1, stats["APT_SEQ_EXISTING_CONFLICT"])

    def test_requests_only_months_with_stored_trades(self):
        rows = {
            "first": [{"district_code": "11680", "deal_date": "2026-09-21"}],
            "second": [{"district_code": "11680", "deal_date": "2026-09-01"},
                       {"district_code": "11170", "deal_date": "2026-08-12"}],
        }
        self.assertEqual(
            [("11170", "202608"), ("11680", "202609")],
            months_with_stored_trades(rows),
        )

    def test_historical_month_window_ends_exclusively_in_next_year(self):
        with patch("link_detailed_unique_road.read_db", return_value=[]) as read:
            load_db("postgres", "202509", "202512")
        query = read.call_args_list[0].args[1]
        self.assertIn("deal_date >= '2025-09-01'", query)
        self.assertIn("deal_date < '2026-01-01'", query)


if __name__ == "__main__":
    unittest.main()
