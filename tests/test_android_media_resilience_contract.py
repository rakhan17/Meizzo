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


if __name__ == "__main__":
    unittest.main(verbosity=2)
