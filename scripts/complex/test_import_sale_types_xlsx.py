import tempfile
import unittest
import zipfile
from pathlib import Path

from import_sale_types_xlsx import read_sale_types


class SaleTypeXlsxTest(unittest.TestCase):
    def test_reads_code_and_sale_type_from_shared_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sample.xlsx"
            shared = (
                '<sst xmlns="http://schemas.openxmlformats.org/'
                'spreadsheetml/2006/main">'
                '<si><t>단지코드</t></si><si><t>분양형태</t></si>'
                '<si><t>A10020758</t></si><si><t>분양</t></si>'
                '<si><t>A10020824</t></si><si><t>혼합</t></si>'
                '</sst>'
            )
            sheet = (
                '<worksheet xmlns="http://schemas.openxmlformats.org/'
                'spreadsheetml/2006/main"><sheetData>'
                '<row r="1"><c r="A1" t="s"><v>0</v></c>'
                '<c r="B1" t="s"><v>1</v></c></row>'
                '<row r="2"><c r="A2" t="s"><v>2</v></c>'
                '<c r="B2" t="s"><v>3</v></c></row>'
                '<row r="3"><c r="A3" t="s"><v>4</v></c>'
                '<c r="B3" t="s"><v>5</v></c></row>'
                '</sheetData></worksheet>'
            )
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("xl/sharedStrings.xml", shared)
                archive.writestr("xl/worksheets/sheet1.xml", sheet)

            self.assertEqual(
                read_sale_types(path),
                {"A10020758": "분양", "A10020824": "혼합"},
            )


if __name__ == "__main__":
    unittest.main()
