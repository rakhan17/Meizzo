import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scripts.release_version import derive_version


class ReleaseVersionTests(unittest.TestCase):
    def test_prerelease_order_is_monotonic(self):
        tags = ["v1.2.3-alpha.1", "v1.2.3-alpha.299",
                "v1.2.3-beta.1", "v1.2.3-beta.299",
                "v1.2.3-rc.1", "v1.2.3-rc.299", "v1.2.3"]
        codes = [derive_version(tag)[1] for tag in tags]
        self.assertEqual(codes, sorted(codes))
        self.assertEqual(len(codes), len(set(codes)))

    def test_v_prefix_is_removed_from_version_name(self):
        self.assertEqual(derive_version("v0.1.0-beta.4")[0], "0.1.0-beta.4")

    def test_unprefixed_tag_is_supported(self):
        self.assertEqual(derive_version("1.0.0"), ("1.0.0", 10_000_999))

    def test_rejects_invalid_prerelease_serials(self):
        for tag in ("1.0.0-alpha.0", "1.0.0-beta.300", "1.0.0-rc.999"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                derive_version(tag)

    def test_rejects_unsupported_tags_and_components(self):
        for tag in ("latest", "v1.0", "v1.100.0", "v1.0.100", "v1.0.0-preview.1"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                derive_version(tag)

    def test_android_version_code_upper_boundary(self):
        self.assertLessEqual(derive_version("209.99.99")[1], 2_100_000_000)
        with self.assertRaises(ValueError):
            derive_version("210.0.0")


if __name__ == "__main__":
    unittest.main()
