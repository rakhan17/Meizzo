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

    def test_pairing_code_keeps_six_fixed_digit_slots(self):
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/ControllerGlassScreen.kt"
        ).read_text()

        self.assertIn("input.text.padEnd(6, '0')", controller)
        self.assertIn("textAlign = TextAlign.Center", controller)
        self.assertIn("OffsetMapping", controller)

    def test_device_controls_live_on_a_separate_detail_screen(self):
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/ControllerGlassScreen.kt"
        ).read_text()

        self.assertIn("var selectedDeviceId by rememberSaveable", controller)
        self.assertIn("selectedDeviceId = device.id", controller)
        self.assertIn("selectedDeviceId = null", controller)
        self.assertIn("‹  Devices", controller)
        self.assertIn("REMOTE SESSIONS", controller)

    def test_client_explains_android_accessibility_restrictions(self):
        client = (
            ROOT
            / "apps/client/src/main/java/com/meizzo/client/ClientGlassScreen.kt"
        ).read_text()

        self.assertIn("Open Accessibility settings", client)
        self.assertIn("Allow restricted settings", client)
        self.assertIn("Open MEIZZO App info", client)

    def test_alarm_toggle_state_survives_controller_restart(self):
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt"
        ).read_text()

        self.assertIn(
            'preferences.getStringSet("alarm_active_clients", emptySet())',
            controller,
        )
        self.assertIn(
            '.putStringSet("alarm_active_clients", alarmActiveClients.toSet())',
            controller,
        )

    def test_media_viewer_can_close_and_reopen_without_ending_approved_session(self):
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt"
        ).read_text()

        self.assertIn("dialog.setOnDismissListener {\n            mediaDialogs.remove(sessionId)\n            hideMediaViewer(sessionId)", controller)
        self.assertIn("if (existingViewer == null) viewer.start(video) else viewer.attachRenderer(video)", controller)
        hide_start = controller.index("private fun hideMediaViewer(sessionId: String)")
        close_start = controller.index("private fun closeMediaViewer(sessionId: String)")
        hide_body = controller[hide_start:close_start]
        self.assertIn("viewer.detachRenderer()", hide_body)
        self.assertNotIn("viewer.close()", hide_body)
        close_body = controller[close_start:controller.index("private fun ", close_start + 1)]
        self.assertIn("mediaViewers.remove(sessionId)?.close()", close_body)

    def test_file_viewer_dismissal_keeps_approved_peer_session_alive(self):
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt"
        ).read_text()

        start = controller.index("private fun openFileTransferDialog(")
        end = controller.index("private fun requestControllerMicrophone(", start)
        file_dialog = controller[start:end]
        self.assertIn("dialog.setOnDismissListener", file_dialog)
        self.assertNotIn("endMediaSession(clientId, sessionId)", file_dialog)
        self.assertIn("if (existingViewer == null) viewer.startFiles()", file_dialog)

    def test_android_client_and_controller_render_wordmark_without_logo(self):
        client = (
            ROOT
            / "apps/client/src/main/java/com/meizzo/client/ClientGlassScreen.kt"
        ).read_text()
        controller = (
            ROOT
            / "apps/controller/src/main/java/com/meizzo/controller/ControllerGlassScreen.kt"
        ).read_text()

        self.assertIn('"MEIZZO"', client)
        self.assertIn('"MEIZZO"', controller)
        self.assertNotIn("painterResource(R.drawable.meizzo_logo)", client)
        self.assertNotIn("painterResource(R.drawable.meizzo_logo)", controller)


if __name__ == "__main__":
    unittest.main(verbosity=2)
