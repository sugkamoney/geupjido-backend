import unittest

from audit_unpriced_cached_road import candidate_status


class CachedRoadCandidateStatusTest(unittest.TestCase):
    def setUp(self):
        self.candidate = {
            "name": "한울네세창아파트",
            "sale_type": "분양",
            "approval_date": "",
        }
        self.trade = {
            "raw_apartment_name": "한울네세창",
            "build_year": "2005",
            "is_canceled": "f",
            "open_conflict": "f",
        }

    def test_confirmed_sale_and_exact_name_allow_missing_approval(self):
        self.assertEqual("POTENTIAL_EXACT_ROAD_NAME",
                         candidate_status(self.candidate, self.trade))

    def test_unknown_sale_type_blocks_missing_approval(self):
        self.candidate["sale_type"] = ""
        self.assertEqual("NAME_REVIEW",
                         candidate_status(self.candidate, self.trade))

    def test_name_difference_blocks_missing_approval(self):
        self.trade["raw_apartment_name"] = "다른단지"
        self.assertEqual("NAME_REVIEW",
                         candidate_status(self.candidate, self.trade))

    def test_known_approval_year_mismatch_allows_exact_name(self):
        self.candidate["approval_date"] = "2006-01-01"
        self.assertEqual("POTENTIAL_EXACT_ROAD_NAME",
                         candidate_status(self.candidate, self.trade))

    def test_year_mismatch_still_requires_exact_name(self):
        self.candidate["approval_date"] = "2006-01-01"
        self.trade["raw_apartment_name"] = "다른단지"
        self.assertEqual("NAME_REVIEW",
                         candidate_status(self.candidate, self.trade))

    def test_canceled_trade_is_not_eligible(self):
        self.trade["is_canceled"] = "t"
        self.assertEqual("CANCELED", candidate_status(self.candidate,
                                                        self.trade))


if __name__ == "__main__":
    unittest.main()
