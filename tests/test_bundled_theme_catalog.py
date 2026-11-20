"""Every theme folder is selectable: the picker catalog and the assets name the same set."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
CATALOG = ROOT / "launcher/app/src/main/assets/theme-catalog.json"
ASSETS = ROOT / "launcher/app/src/main/assets/upstream-themes"


class BundledThemeCatalogTest(unittest.TestCase):
    def test_catalog_lists_every_theme_folder_in_both_languages(self):
        folders = {p.name for p in THEMES.iterdir() if p.is_dir()}
        data = json.loads(CATALOG.read_text(encoding="utf-8"))
        items = data["themes"]
        ids = [item["id"] for item in items]
        self.assertEqual(folders, set(ids))
        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreaterEqual(len(ids), 29)
        for item in items:
            self.assertTrue(item["name_en"].strip())
            self.assertTrue(item["name_ar"].strip())
            self.assertNotEqual(item["name_en"].strip(), item["name_ar"].strip())
            config = ASSETS / item["id"] / "config.json"
            self.assertTrue(config.is_file(), config)
            # The repo copy and the asset copy of config.json are the same file.
            self.assertEqual((THEMES / item["id"] / "config.json").read_bytes(), config.read_bytes())


if __name__ == "__main__":
    unittest.main()
