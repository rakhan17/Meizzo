"""Static regression checks for the Android remote-alarm command path.

These checks protect wiring and permission declarations. They do not replace
physical-device audio, background, or OEM acceptance tests.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AndroidAlarmContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = (ROOT / "apps/client/src/main/java/com/meizzo/client/ConnectionForegroundService.kt").read_text()
        cls.client = (ROOT / "apps/client/src/main/java/com/meizzo/client/MainActivity.kt").read_text()
        cls.controller = (ROOT / "apps/controller/src/main/java/com/meizzo/controller/MainActivity.kt").read_text()
        cls.manifest = (ROOT / "apps/client/src/main/AndroidManifest.xml").read_text()

    def test_alarm_audio_is_looped_and_released(self):
        self.assertIn("AudioTrack.MODE_STATIC", self.service)
        self.assertIn("setLoopPoints(0, samples.size, -1)", self.service)
        self.assertIn("track.release()", self.service)
        self.assertNotIn("ToneGenerator", self.client)

    def test_alarm_uses_declared_media_playback_foreground_type(self):
        self.assertIn("FOREGROUND_SERVICE_MEDIA_PLAYBACK", self.manifest)
        self.assertIn('android:foregroundServiceType="connectedDevice|mediaPlayback"', self.manifest)
        self.assertIn("FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK", self.service)

    def test_local_notification_can_stop_alarm(self):
        self.assertIn('builder.addAction(android.R.drawable.ic_media_pause, "Stop alarm", stopAlarm)', self.service)
        self.assertIn("ACTION_ALARM_OFF", self.service)

    def test_client_acknowledges_only_after_service_result(self):
        self.assertIn("ACTION_ALARM_RESULT", self.service)
        self.assertIn("ContextCompat.registerReceiver", self.client)
        self.assertIn("pendingAlarmCommands.remove(requestId)", self.client)
        self.assertIn('put("type", "client.result")', self.client)

    def test_controller_waits_for_client_result(self):
        self.assertIn("pendingCommandClients.add(clientId)", self.controller)
        self.assertIn("Waiting for Client confirmation", self.controller)
        self.assertIn("Client confirmed · command completed", self.controller)
        self.assertNotIn('setConnection("Command sent · $command', self.controller)


if __name__ == "__main__":
    unittest.main()
