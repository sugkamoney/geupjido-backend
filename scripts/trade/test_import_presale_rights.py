import unittest
import xml.etree.ElementTree as ET

from import_presale_rights import month_range, parse_matched_item


class PresaleRightsImportTest(unittest.TestCase):
    def setUp(self):
        self.complex = {
            "id": "A10020363", "name": "청담르엘",
            "legal_dong_code": "1168010400", "zone_id": "gangnam-cheongdam",
        }

    def item(self, name="청담 르엘", canceled=" ", cancellation_date=" "):
        return ET.fromstring(f"""
            <item><sggCd>11680</sggCd><umdNm>청담동</umdNm>
            <jibun>134-18</jibun><aptNm>{name}</aptNm>
            <dealYear>2026</dealYear><dealMonth>2</dealMonth>
            <dealDay>26</dealDay><dealAmount>540,000</dealAmount>
            <excluUseAr>84.9194</excluUseAr><floor>30</floor>
            <ownershipGbn>입</ownershipGbn><cdealType>{canceled}</cdealType>
            <cdealDay>{cancellation_date}</cdealDay></item>
        """)

    def parse(self, item):
        return parse_matched_item(
            item, 3, "202602", self.complex, "청담동", "134-18"
        )

    def test_matched_right_is_kept_with_source_kind(self):
        row = self.parse(self.item())
        self.assertEqual("A10020363", row[0])
        self.assertEqual(3, row[4])
        self.assertEqual(540000, row[6])
        self.assertEqual("입", row[12])
        self.assertFalse(row[13])

    def test_canceled_duplicate_is_retained_as_separate_source_row(self):
        normal = self.parse(self.item())
        canceled = self.parse(self.item(canceled="O",
                                        cancellation_date="26.03.05"))
        self.assertFalse(normal[13])
        self.assertTrue(canceled[13])
        self.assertEqual("2026-03-05", canceled[14])

    def test_different_name_is_not_assigned_to_complex(self):
        self.assertIsNone(self.parse(self.item(name="다른아파트")))

    def test_unknown_cancellation_code_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "알 수 없는 거래 해제 구분"):
            self.parse(self.item(canceled="X"))

    def test_month_sequence_includes_year_boundary(self):
        self.assertEqual(["202512", "202601", "202602"],
                         list(month_range("202512", "202602")))


if __name__ == "__main__":
    unittest.main()
