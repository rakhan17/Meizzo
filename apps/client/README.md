# MEIZZO Client

The managed-device Android application. The current milestone generates a secure
local device identity, publishes a short-lived six-digit pairing offer, displays
connection state, and requires explicit approval before authorization tokens are
issued.

Debug builds default to `ws://10.0.2.2:8080` for an Android emulator or
BlueStacks instance. Release builds require
`-Pmeizzo.releaseSignalingUrl=wss://your-host.example`; they do not permit
cleartext traffic.
Change `SIGNALING_URL` in `build.gradle.kts` for a deployed WSS service.
