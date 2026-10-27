import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import import_locations
import summarize_location_runs


class LocationManifestTest(unittest.TestCase):
    def test_abort_keeps_last_completed_cursor_for_resume(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "runs.jsonl"
            rows = [
                {"complexId": "A1", "resultKind": "NEW_LOCATION_SAVED",
                 "zoneStatus": "MATCHED", "nextAfterId": "A1"},
                {"complexId": "A2", "resultKind": "ABORTED",
                 "zoneStatus": None, "nextAfterId": "A1"},
            ]
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))

            self.assertEqual(import_locations.latest_cursor(path), "A1")

    def test_summary_uses_latest_attempt_per_complex(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "runs.jsonl"
            rows = [
                {"complexId": "A1", "resultKind": "ABORTED", "zoneStatus": None},
                {"complexId": "A1", "resultKind": "NEW_LOCATION_SAVED",
                 "zoneStatus": "MATCHED"},
                {"complexId": "A2", "resultKind": "MULTIPLE_GEOCODING_RESULTS",
                 "zoneStatus": None},
            ]
            path.write_text("".join(json.dumps(row) + "\n" for row in rows))

            attempts, latest, kinds, statuses = summarize_location_runs.summarize(path)

            self.assertEqual(attempts, 3)
            self.assertEqual(len(latest), 2)
            self.assertEqual(kinds["NEW_LOCATION_SAVED"], 1)
            self.assertEqual(kinds["MULTIPLE_GEOCODING_RESULTS"], 1)
            self.assertEqual(statuses["MATCHED"], 1)
            self.assertEqual(statuses["NONE"], 1)


if __name__ == "__main__":
    unittest.main()
