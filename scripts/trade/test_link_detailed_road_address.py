import importlib.util
import unittest
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("link_detailed_road_address.py")
spec = importlib.util.spec_from_file_location("detail_address_link", MODULE_PATH)
detail_link = importlib.util.module_from_spec(spec)
spec.loader.exec_module(detail_link)


class DetailedRoadAddressLinkTest(unittest.TestCase):
    def setUp(self):
        self.item = ET.fromstring("""
            <item>
                <sggCd>41173</sggCd><umdCd>10100</umdCd>
                <roadNm>비산로</roadNm><roadNmBonbun>00022</roadNmBonbun>
                <roadNmBubun>00000</roadNmBubun>
            </item>
        """)
        self.group = {
            "후보단지ID": "A10023993",
            "후보단지명": "평촌자이아이파크",
            "후보도로명주소": "경기도 안양시 동안구 비산로 22",
            "후보사용승인일": "2021-12-29",
            "법정동코드": "4117310100",
            "법정동명": "비산동",
            "권역ID": "dongan-bisan",
        }
        self.candidate = {
            "name": self.group["후보단지명"],
            "address_road": self.group["후보도로명주소"],
            "approval_date": self.group["후보사용승인일"],
            "legal_dong_code": self.group["법정동코드"],
            "zone_id": self.group["권역ID"],
            "sale_type": "분양",
        }
        self.db_row = {"legal_dong_name": "비산동", "build_year": "2021",
                       "raw_apartment_name": "평촌자이아이파크"}

    def assess(self, candidate=None, road_counts=None):
        selected = candidate or self.candidate
        counts = road_counts or Counter({
            (selected["legal_dong_code"], selected["address_road"]): 1
        })
        return detail_link.assess_item(
            self.item, self.group, {"A10023993": selected},
            counts, self.db_row,
        )

    def test_exact_road_legal_dong_and_year_match(self):
        self.assertEqual("MATCHED", self.assess())
        self.assertFalse(detail_link.address_matches(
            self.item, "경기도 안양시 동안구 비산로 220"
        ))

    def test_shared_road_address_is_not_approved(self):
        counts = Counter({("4117310100", "경기도 안양시 동안구 비산로 22"): 2})
        self.assertEqual("SHARED_ROAD_ADDRESS", self.assess(road_counts=counts))

    def test_build_year_difference_with_same_name_is_approved(self):
        self.db_row["build_year"] = "2022"
        self.db_row["raw_apartment_name"] = "평촌자이아이파크"
        self.assertEqual("MATCHED", self.assess())

    def test_build_year_difference_with_different_name_requires_review(self):
        self.db_row["build_year"] = "2022"
        self.db_row["raw_apartment_name"] = "다른단지"
        self.assertEqual("NAME_REVIEW", self.assess())

    def test_rental_only_is_not_approved(self):
        self.candidate["sale_type"] = "임대"
        self.assertEqual("RENTAL_ONLY", self.assess())

    def test_different_road_number_is_not_approved(self):
        self.item.find("roadNmBonbun").text = "00023"
        self.assertEqual("ROAD_MISMATCH", self.assess())


if __name__ == "__main__":
    unittest.main()
