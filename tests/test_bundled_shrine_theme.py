"""The shrine theme is shipped inside the launcher APK and applied when no theme is present.

The saved Haier theme is the dock, columns, dim, opacity and glass rim below, plus one wallpaper.
wallpaper_rotation_minutes is 0 so a single picture never rotates. There is no wallpapers/ collection.
"""
import json
import struct
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / "launcher" / "app" / "src" / "main" / "assets" / "bundled-theme"
CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"
BUNDLED_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "BundledTheme.java"

DOCK = [
    "com.google.android.youtube.tv",
    "net.mbc.shahidTV",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "com.osn.go",
    "com.disneyplus.mea",
]


def jpeg_size(data):
    if data[:2] != b"\xff\xd8":
        return None
    i = 2
    while i + 4 <= len(data):
        if data[i] != 0xFF:
            return None
        marker = data[i + 1]
        length = struct.unpack(">H", data[i + 2:i + 4])[0]
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height, width = struct.unpack(">HH", data[i + 5:i + 9])
            return width, height
        i += 2 + length
    return None


class BundledShrineThemeTest(unittest.TestCase):
    def test_config_matches_the_saved_haier_theme(self):
        config = json.loads((ASSET / "config.json").read_text(encoding="utf-8"))
        self.assertEqual(DOCK, config["dock_apps"])
        self.assertEqual(6, config["columns"])
        self.assertIs(True, config["wallpaper"])
        self.assertEqual(45, config["wallpaper_dim"])
        self.assertEqual(70, config["dock_opacity"])
        self.assertEqual("rim", config["glass_style"])
        self.assertEqual(0, config["wallpaper_rotation_minutes"])
        self.assertNotIn("wallpaper_moods", config)
        self.assertNotIn("wallpapers", config)

    def test_single_wallpaper_and_credit(self):
        names = sorted(p.name for p in ASSET.iterdir())
        self.assertEqual(["CREDITS.md", "config.json", "wallpaper.jpg"], names)
        data = (ASSET / "wallpaper.jpg").read_bytes()
        self.assertGreater(len(data), 1000)
        self.assertEqual((1920, 1080), jpeg_size(data))
        credits = (ASSET / "CREDITS.md").read_text(encoding="utf-8")
        self.assertIn("CC BY-SA 4.0", credits)
        self.assertIn("Hassan Ehap", credits)

    def test_loader_installs_the_bundle_before_reading_the_overlay(self):
        source = CONFIG_JAVA.read_text(encoding="utf-8")
        install = source.index("BundledTheme.installIfAbsent(ctx)")
        read = source.index("readOverlay(ctx, file, mounted)")
        self.assertLess(install, read)
        bundled = BUNDLED_JAVA.read_text(encoding="utf-8")
        self.assertIn("config.isFile() && config.length() > 0", bundled)
        # The copy must not invent a collection directory. The comment may name wallpapers/.
        self.assertNotIn('new File(dir, "wallpapers")', bundled)


if __name__ == "__main__":
    unittest.main()
