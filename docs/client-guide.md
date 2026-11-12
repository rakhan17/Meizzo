# MEIZZO Client guide

The v3 Android beta candidate uses a focused single-screen pairing-code layout with a shared Liquid Glass presentation layer. Pairing and sensitive-operation approvals remain explicit on the Client. App-owned prompts use the shared glass dialog; Android system permission and document pickers remain native.

Client shows the device identity, temporary pairing code, connection state, and
approval prompts. It registers a pairing offer with the signaling backend and
sends authenticated heartbeat telemetry after authorization.

## Consent flow

The Client must display and approve each pairing request. Rejecting or
dismissing a request does not issue a Controller token. The Client does not
silently grant capture or control permissions.

## Connection lifecycle

Client states are driven by WebSocket callbacks and backend authentication:
connecting, connected/registering, authorized, disconnected, and reconnecting.
Reconnect attempts use exponential backoff capped at six stages. Heartbeats
are sent every 15 seconds while the socket is active; the backend marks a
Client stale after 45 seconds without a heartbeat.

## Supported commands

The current Android Client handles flashlight control when camera permission
and hardware allow it, and an alarm tone command. Unsupported hardware or
permissions return an error to Controller. iOS does not claim these Android
commands.

## Local data

The app-generated Client ID is kept in app preferences. The authorization
token is stored in Android Keystore-backed `SecureStore`. Removing app data
removes local authorization.
