# MEIZZO Controller

The operator Android application. The current milestone connects to the
signaling service, accepts a six-digit code, reports pending/failed/paired
states, and stores the returned paired-device record locally.

The signaling endpoint defaults to `ws://10.0.2.2:8080` for an Android emulator.
Change `SIGNALING_URL` in `build.gradle.kts` for a deployed WSS service.
