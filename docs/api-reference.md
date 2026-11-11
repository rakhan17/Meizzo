# Signaling API reference

MEIZZO uses JSON messages over WebSocket. The hosted Worker accepts upgrades
at `/` and `/ws`, and exposes `GET /healthz`. The server greeting
(`system.hello`) advertises protocol version 2 and server capabilities.

The hosted Worker in `services/signaling/cloudflare/worker.js` and the local
Node.js harness in `services/signaling/src/server.js` must remain compatible.

## Connection

- Transport: WSS for signaling in the hosted Android configuration.
- Media: WebRTC peer-to-peer video and DataChannel after Client approval.
- Protocol version: `2`.
- Session authorization is participant- and connection-bound.
- Approved media sessions receive `media.ice_servers` before session status;
  this includes Cloudflare STUN and optional short-lived TURN credentials.

## Client messages

| Type | Purpose |
|---|---|
| `client.register_pairing_offer` | Register a six-digit offer and expiry |
| `client.pairing_decision` | Approve or reject a pending pairing request |
| `auth.connect` | Authenticate as Client using its issued token |
| `heartbeat` | Refresh presence and optionally send telemetry |
| `client.session_decision` | Approve or reject a pending session |
| `client.end_session` | End an approved media session from the Client side |
| `client.result` | Return a snapshot or command result |
| `media.signal` | Send WebRTC offer/answer/ICE or stream metadata for an approved media session |

## Controller messages

| Type | Purpose |
|---|---|
| `controller.request_pairing` | Submit a Client's six-digit code |
| `auth.connect` | Authenticate as Controller using its issued token |
| `controller.list_devices` | Retrieve authorized paired Clients |
| `controller.rename_device` | Set a local display alias |
| `controller.revoke_device` | Revoke the Controller's relationship with a Client |
| `controller.request_snapshot` | Request the current Client snapshot |
| `controller.start_session` | Request a scoped session from a Client |
| `controller.end_session` | End a session owned by the authenticated Controller |
| `controller.command` | Send a supported command, subject to authorization/session checks |
| `media.signal` | Send WebRTC offer/answer/ICE for an approved media session |

## Server events

| Type | Meaning |
|---|---|
| `system.hello` | Protocol version and server capabilities |
| `client.pairing_request` | Pairing approval request delivered to Client |
| `client.pairing_completed` | Client pairing completed |
| `controller.pairing_pending` | Pairing request awaits Client decision |
| `controller.pairing_result` | Pairing result or failure |
| `auth.connected` | Authentication accepted |
| `controller.status` | Controller connection status |
| `controller.device_list` | Authorized device snapshot |
| `device.status` | Presence and metadata update |
| `device.result` | Result of a Client request |
| `device.renamed` | Alias update acknowledged |
| `device.revoked` | Relationship revoked |
| `session.status` | Controller-side session state |
| `client.session_status` | Client-side session state |
| `media.signal` | Authenticated peer's WebRTC signaling payload |
| `media.ice_servers` | Session-scoped STUN/TURN ICE server configuration delivered to both approved peers |
| `heartbeat.ack` | Heartbeat acknowledgement |
| `error` | Machine-readable error code and message |

## Session types and states

The Worker recognizes `monitoring`, `screen`, `camera`, `interaction`,
and `files`. Session states include `pending`, `approved`, `rejected`,
`failed`, and `ended`.

- `screen`: Client MediaProjection video track.
- `camera`: Client Camera2 video track.
- `interaction`: authorization for supported remote input and lock commands.
- `files`: ordered WebRTC DataChannel for user-selected file transfer.
- `monitoring`: telemetry/monitoring authorization.

Only approved screen, camera, or files sessions accept `media.signal`.
The Worker checks that the sender is the authenticated session participant
and relays only signaling messages. It does not relay media or file bytes.

## WebRTC signal payloads

The `media.signal` envelope is:

```json
{
  "type": "media.signal",
  "sessionId": "approved-session-id",
  "signal": {
    "type": "offer",
    "sdp": "..."
  }
}
```

Supported signal payload types include:

- `offer` and `answer` with SDP text.
- `candidate` with `sdpMid`, `sdpMLineIndex`, and ICE candidate text.
- `stream_info` with the Client display width and height.
- `error` with a short failure message.

Payloads must be JSON objects and are size-limited by the Worker.

## Pairing and authorization notes

- Pairing codes are six digits and expire within a maximum ten-minute window.
- The hosted Worker applies attempt limits per Controller and source IP.
- A pairing code is consumed after successful approval.
- Client and Controller receive separate random authentication tokens.
- Controller access is checked against the specific Client relationship.
- Revocation removes the relevant Controller tokens and relationship.
- Session signaling is bound to the session participants and their current
  authenticated socket connections.

## Errors and compatibility

Errors use `type: "error"` with a machine-readable `code` and a
human-readable `message`. Do not add a message type to this document until
it exists in both the hosted Worker, local harness, and automated tests.
