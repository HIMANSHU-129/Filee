import os
import sys
import unittest
from pathlib import Path
from PIL import Image

SRC_PYTHON = Path(__file__).resolve().parent.parent.parent / "main" / "python"
if str(SRC_PYTHON) not in sys.path:
    sys.path.insert(0, str(SRC_PYTHON))

from converters.images.image_converter import convert_image, convert_images_to_pdf
from converters.documents.document_converter import convert_document
from core.registry import ConverterRegistry


class TestPdfConversions(unittest.TestCase):

    def setUp(self):
        self.test_dir = Path(__file__).resolve().parent / "pdf_test_scratch"
        self.test_dir.mkdir(exist_ok=True)
        self.img1 = self.test_dir / "img1.png"
        self.img2 = self.test_dir / "img2.jpg"

        im1 = Image.new("RGBA", (100, 100), (255, 0, 0, 200))
        im1.save(self.img1)

        im2 = Image.new("RGB", (120, 80), (0, 128, 255))
        im2.save(self.img2)

    def tearDown(self):
        if self.test_dir.exists():
            for f in self.test_dir.glob("*"):
                try:
                    f.unlink()
                except Exception:
                    pass
            try:
                self.test_dir.rmdir()
            except Exception:
                pass

    def test_single_image_to_pdf(self):
        out_pdf = self.test_dir / "single.pdf"
        result = convert_image(str(self.img1), str(out_pdf), "pdf")
        self.assertTrue(result["success"], f"Single image to PDF failed: {result.get('error')}")
        self.assertTrue(out_pdf.exists())
        self.assertGreater(out_pdf.stat().st_size, 0)

    def test_multiple_images_to_pdf(self):
        out_pdf = self.test_dir / "combined.pdf"
        result = convert_images_to_pdf([str(self.img1), str(self.img2)], str(out_pdf))
        self.assertTrue(result["success"], f"Multi image to PDF failed: {result.get('error')}")
        self.assertTrue(out_pdf.exists())
        self.assertGreater(out_pdf.stat().st_size, 0)
        self.assertEqual(result.get("page_count"), 2)

    def test_pdf_to_images(self):
        # First generate a multi-page PDF
        pdf_path = self.test_dir / "sample_doc.pdf"
        convert_images_to_pdf([str(self.img1), str(self.img2)], str(pdf_path))

        # Convert PDF to PNG
        out_png = self.test_dir / "rendered.png"
        result = convert_document(str(pdf_path), str(out_png), "png")
        self.assertTrue(result["success"], f"PDF to PNG failed: {result.get('error')}")
        self.assertIn("output_files", result)
        self.assertEqual(len(result["output_files"]), 2)
        for f in result["output_files"]:
            self.assertTrue(Path(f).exists())
            self.assertGreater(Path(f).stat().st_size, 0)

    def test_registry_pdf_conversions(self):
        reg = ConverterRegistry()
        conversions = reg.list_supported_conversions()
        self.assertIn("pdf", conversions["png"])
        self.assertIn("pdf", conversions["jpg"])
        self.assertIn("png", conversions["pdf"])
        self.assertIn("jpg", conversions["pdf"])
        self.assertIn("webp", conversions["pdf"])


if __name__ == "__main__":
    unittest.main()
