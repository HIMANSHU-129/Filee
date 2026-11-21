import os
import sys
import unittest
from pathlib import Path
from PIL import Image

# Ensure main python directory is on sys.path
SRC_PYTHON = Path(__file__).resolve().parent.parent.parent / "main" / "python"
if str(SRC_PYTHON) not in sys.path:
    sys.path.insert(0, str(SRC_PYTHON))

from converters.images.image_converter import convert_image, FORMAT_MAP
from core.registry import ConverterRegistry


class TestImageConverter(unittest.TestCase):

    def setUp(self):
        self.test_dir = Path(__file__).resolve().parent / "test_scratch"
        self.test_dir.mkdir(exist_ok=True)
        self.sample_png = self.test_dir / "sample.png"

        # Create sample RGBA image
        img = Image.new("RGBA", (64, 64), (120, 180, 240, 200))
        img.save(self.sample_png)

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

    def test_all_format_conversions(self):
        formats = ["jpg", "jpeg", "webp", "gif", "bmp", "tiff", "ico", "png"]
        for fmt in formats:
            out_file = self.test_dir / f"output_{fmt}.{fmt}"
            progress_calls = []

            def progress_cb(val, stage):
                progress_calls.append((val, stage))

            result = convert_image(
                str(self.sample_png),
                str(out_file),
                fmt,
                options={"quality": 85},
                progress_callback=progress_cb
            )

            self.assertTrue(result["success"], f"Format {fmt} failed: {result.get('error')}")
            self.assertTrue(out_file.exists(), f"Output {out_file} does not exist")
            self.assertGreater(out_file.stat().st_size, 0)
            self.assertGreater(len(progress_calls), 0)

    def test_missing_source_file(self):
        result = convert_image("non_existent_file.png", "out.jpg", "jpg")
        self.assertFalse(result["success"])
        self.assertIn("does not exist", result["error"])

    def test_unsupported_format(self):
        result = convert_image(str(self.sample_png), "out.xyz", "xyz")
        self.assertFalse(result["success"])
        self.assertIn("Unsupported", result["error"])

    def test_registry_registration(self):
        registry = ConverterRegistry()
        conversions = registry.list_supported_conversions()
        self.assertIn("png", conversions)
        self.assertIn("jpg", conversions["png"])
        self.assertIn("webp", conversions["png"])


if __name__ == "__main__":
    unittest.main()
