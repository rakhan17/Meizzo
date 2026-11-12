#!/usr/bin/env python3
"""Source-contract checks for Android WebRTC resilience safeguards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class MediaResilienceContract(unittest.TestCase):
    def test_client_adapts_sender_bitrate_from_webrtc_stats(self):
        source = (ROOT / "apps/client/src/main/java/com/meizzo/client/RtcMediaClient.kt").read_text()
        for marker in (
            "connection.getStats",
            "availableOutgoingBitrate",
            "fractionLost",
            "currentRoundTripTime",
            "sender.setParameters(parameters)",
            "adaptationHandler.removeCallbacks(adaptationPoll)",
        ):
            self.assertIn(marker, source)

    def test_controller_uses_bounded_ice_restart_recovery(self):
        source = (ROOT / "apps/controller/src/main/java/com/meizzo/controller/RtcMediaController.kt").read_text()
        for marker in (
            "IceConnectionState.DISCONNECTED",
            "IceConnectionState.FAILED",
            'KeyValuePair("IceRestart", "true")',
            "MAX_ICE_RESTART_ATTEMPTS = 3",
            "scheduleIceRestartRetry()",
            "recoveryHandler.removeCallbacks(iceRestartTask)",
        ):
            self.assertIn(marker, source)


    def test_remote_two_finger_scroll_is_wired_end_to_end(self):
        controller = (ROOT / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt").read_text()
        client = (ROOT / "apps/client/src/main/java/com/meizzo/client/MainActivity.kt").read_text()
        accessibility = (ROOT / "apps/client/src/main/java/com/meizzo/client/RemoteAccessibilityService.kt").read_text()
        for marker in (
            "ACTION_POINTER_DOWN",
            'sendInteractionAction(clientId, "scroll"',
            "multiTouchGesture",
        ):
            self.assertIn(marker, controller)
        self.assertIn('"scroll" -> {', client)
        self.assertIn("fun scroll(deltaX: Double, deltaY: Double): Boolean", accessibility)
        self.assertIn("return swipe(startX, startY, endX, endY, 220L)", accessibility)

    def test_accessibility_key_events_use_valid_android_constructor(self):
        source = (ROOT / "apps/client/src/main/java/com/meizzo/client/RemoteAccessibilityService.kt").read_text()
        self.assertIn("val downTime = android.os.SystemClock.uptimeMillis()", source)
        self.assertIn("KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)", source)
        self.assertIn("KeyEvent.ACTION_UP", source)


    def test_client_websocket_lifecycle_is_owned_by_foreground_service(self):
        service = (ROOT / "apps/client/src/main/java/com/meizzo/client/ConnectionForegroundService.kt").read_text()
        activity = (ROOT / "apps/client/src/main/java/com/meizzo/client/MainActivity.kt").read_text()
        for marker in (
            "private var socket: WebSocket? = null",
            "fun attachSocketListener",
            "fun detachSocketListener",
            "private fun connectSocket()",
            "scheduleSocketReconnect()",
            "registerDefaultNetworkCallback(networkCallback)",
            "startSocketHeartbeat(webSocket)",
            "MAX_QUEUED_MESSAGES",
        ):
            self.assertIn(marker, service)
        self.assertIn("ConnectionForegroundService.LocalBinder", activity)
        self.assertIn("connectionService?.attachSocketListener", activity)
        self.assertIn("connectionService?.detachSocketListener()", activity)
        self.assertNotIn("socket?.close(1000, \"Client activity destroyed\")", activity)

    def test_file_transfer_persists_and_resumes_acknowledged_offsets(self):
        for role in ("client", "controller"):
            source = (ROOT / f"apps/{role}/src/main/java/com/meizzo/{role}/FileTransferCoordinator.kt").read_text()
            for marker in (
                'getSharedPreferences("meizzo_file_transfer_resume"',
                '"outgoing_offset"',
                '"incoming_offset"',
                "resumeOutgoingOfferIfPresent()",
                "restoreOutgoingCheckpoint(transferId, offset)",
                "restoreIncomingCheckpoint(transferId)",
                'put("offset", offset)',
                'openOutputStream(uri, "wa")',
                "pauseActiveTransfer()",
                "MessageDigest.getInstance(\"SHA-256\")",
            ):
                self.assertIn(marker, source)

    def test_document_permissions_are_persisted_for_transfer_resume(self):
        client = (ROOT / "apps/client/src/main/java/com/meizzo/client/MainActivity.kt").read_text()
        controller = (ROOT / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt").read_text()
        self.assertIn("takePersistableUriPermission(selectedUri, persistableFlags)", client)
        self.assertIn("takePersistableUriPermission(uri, persistableFlags)", controller)
        self.assertIn("takePersistableUriPermission(destination, persistableFlags)", client)
        self.assertIn("takePersistableUriPermission(destination, persistableFlags)", controller)

    def test_file_transfer_rejects_oversized_and_overdeclared_chunks(self):
        for role in ("client", "controller"):
            source = (ROOT / f"apps/{role}/src/main/java/com/meizzo/{role}/FileTransferCoordinator.kt").read_text()
            self.assertIn("MAX_ENCODED_CHUNK_SIZE", source)
            self.assertIn("bytes.size > CHUNK_SIZE", source)
            self.assertIn("incomingBytes + bytes.size > pendingSize", source)
            self.assertIn("MessageDigest.getInstance(\"SHA-256\")", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
