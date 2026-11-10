# MEIZZO V2 architecture

V2 keeps Client and Controller as separate applications and treats signaling,
identity, session authorization, telemetry, and media as distinct boundaries.

## Control plane

The current Node.js service is the control-plane prototype. It accepts
WebSocket messages for pairing, authentication, presence, telemetry, device
listing, commands, and revocation. Protocol version `2` and a capability list
are now advertised in `system.hello` and `auth.connected`.

The next production step is to move identity, paired-device relationships,
pending requests, and session records from process memory to a durable store.
The current implementation must remain single-instance until that migration is
complete.

The current protocol now includes an authorization-only session lifecycle:
`controller.start_session`, Client approval through
`client.session_decision`, `session.status`/`client.session_status`, and
`controller.end_session`. An `approved` session is not a media connection;
WebRTC and OS permission flows remain separate future capabilities.

## Media plane

Screen and camera media are intentionally not represented as working features
yet. The V2 boundary is:

1. Client requests OS permission.
2. Client creates a consented session.
3. Signaling exchanges WebRTC metadata.
4. STUN/TURN establishes transport.
5. Client owns capture and releases it when the session ends.

No component may infer that a connected WebSocket authorizes screen capture or
input control.

## Android application boundaries

Each app should converge on:

- `presentation`: state-driven screens and navigation.
- `domain`: pairing, devices, sessions, capabilities, and diagnostics.
- `data`: WebSocket transport, secure identity, telemetry, and update clients.
- `platform`: MediaProjection, Accessibility, camera, location, and SAF
  adapters with explicit capability checks.

The current repository has these responsibilities in `MainActivity`; the
migration should extract them incrementally while preserving package IDs and
backend contract tests.
