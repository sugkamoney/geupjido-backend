import unittest
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

import link_detailed_apt_seq as linker


def api_item(day):
    return ET.fromstring(f"""
        <item>
          <sggCd>11110</sggCd><umdCd>17500</umdCd>
          <aptSeq>11110-2234</aptSeq><aptNm>종로청계힐스테이트</aptNm>
          <jibun>766</jibun><dealAmount>126,000</dealAmount>
          <excluUseAr>84.95</excluUseAr><floor>14</floor>
          <buildYear>2009</buildYear><dealYear>2026</dealYear>
          <dealMonth>9</dealMonth><dealDay>{day}</dealDay>
          <cdealType></cdealType>
        </item>
    """)


class AptSeqBridgeTest(unittest.TestCase):
    def setUp(self):
        linked = api_item(23)
        unlinked = api_item(24)
        self.items = [linked, unlinked]
        self.db = defaultdict(list, {
            linker.trade_key_from_api(linked): [{
                "id": "1", "complex_id": "A11055002",
                "zone_id": "jongro-changsin-sungin",
                "district_code": "11110", "build_year": "2009",
            }],
            linker.trade_key_from_api(unlinked): [{
                "id": "2", "complex_id": "", "zone_id": "",
                "district_code": "11110", "build_year": "2009",
            }],
        })
        self.complexes = {"A11055002": {
            "legal_dong_code": "1111017500",
            "zone_id": "jongro-changsin-sungin",
            "approval_date": "2009-01-01", "sale_type": "분양",
        }}

    def audit(self):
        with patch.object(linker, "load_months", return_value=[("11110", "202609")]), \
                patch.object(linker, "fetch_detail", return_value=self.items):
            return linker.collect_plan("unused", self.db, self.complexes)

    def test_verified_bridge_links_same_apt_seq(self):
        plan, stats = self.audit()
        self.assertEqual([(2, "11110-2234", "A11055002",
                           "jongro-changsin-sungin")], plan)
        self.assertEqual(0, stats["ambiguous_existing_bridge_count"])

    def test_different_legal_dong_is_not_linked(self):
        self.complexes["A11055002"]["legal_dong_code"] = "1111017400"
        plan, stats = self.audit()
        self.assertEqual([], plan)
        self.assertEqual(1, stats["legal_dong_mismatch"])

    def test_verified_bridge_ignores_approval_year(self):
        self.complexes["A11055002"]["approval_date"] = "2025-01-01"
        plan, _ = self.audit()
        self.assertEqual(1, len(plan))

    def test_successful_detail_response_is_reused_after_retry(self):
        with TemporaryDirectory() as directory, \
                patch.object(linker, "fetch_detail", return_value=self.items) as fetch:
            first = linker.load_cached_detail("unused", "11110", "202609",
                                             Path(directory))
            second = linker.load_cached_detail("unused", "11110", "202609",
                                              Path(directory))
        self.assertEqual(2, len(first))
        self.assertEqual(["11110-2234", "11110-2234"],
                         [item.findtext("aptSeq") for item in second])
        fetch.assert_called_once()


if __name__ == "__main__":
    unittest.main()
