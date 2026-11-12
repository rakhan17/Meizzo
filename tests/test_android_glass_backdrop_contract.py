"""Regression checks for safe Backdrop source/surface layering and branding."""

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
        self.assertIn("Box(Modifier.fillMaxSize()) {", source)
        self.assertIn("content(backdrop)", source)
        self.assertIn("Record only the background as the backdrop source", source)
        self.assertIn("Glass surfaces must", source)

    def test_android_client_and_controller_render_the_brand_logo(self):
        client = (
            ROOT
            / "apps/client/src/main/java/com/meizzo/client/ClientGlassScreen.kt"
        ).read_text()
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/ControllerGlassScreen.kt"
        ).read_text()

        self.assertIn("painterResource(R.drawable.meizzo_logo)", client)
        self.assertIn("painterResource(R.drawable.meizzo_logo)", controller)


if __name__ == "__main__":
    unittest.main(verbosity=2)
