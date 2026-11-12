"""Regression checks for safe Backdrop source/surface layering on Android."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AndroidGlassBackdropContractTests(unittest.TestCase):
    def test_backdrop_source_is_a_sibling_of_glass_surfaces(self):
        source = (
            ROOT
            / "apps/glass-ui/src/main/java/com/meizzo/glass/MeizzoGlass.kt"
        ).read_text()

        root_box = source.index("Box(Modifier.fillMaxSize()) {")
        source_layer = source.index(".layerBackdrop(backdrop)")
        glass_content = source.index("content(backdrop)")

        self.assertEqual(source.count(".layerBackdrop(backdrop)"), 1)
        self.assertLess(root_box, source_layer)
        self.assertLess(source_layer, glass_content)
        self.assertIn("Record only the background as the backdrop source", source)
        self.assertIn("Glass surfaces must", source)

    def test_glass_content_is_not_recorded_into_its_own_backdrop(self):
        source = (
            ROOT
            / "apps/glass-ui/src/main/java/com/meizzo/glass/MeizzoGlass.kt"
        ).read_text()

        self.assertNotIn(
            ".fillMaxSize()\n            .layerBackdrop(backdrop)\n            .background(",
            source,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
