# Signaling API reference

MEIZZO uses JSON messages over WebSocket. The hosted Worker accepts upgrades
at `/` and `/ws`, and exposes `GET /healthz`. The server greeting
(`system.hello`) advertises protocol version 2 and the currently implemented
capabilities.

The hosted Worker implementation in
`services/signaling/cloudflare/worker.js` is authoritative for the deployed
endpoint. The Node.js harness in `services/signaling/src/server.js` is used
for local development and tests; keep both implementations compatible when
changing message contracts.

## Connection

- Transport: WebSocket Secure (WSS) for the published Android release.
- Health: `GET /healthz`.
- Protocol version: `2`.
- Server capabilities: pairing, token authentication, presence, telemetry,
  device commands, and multi-device routing.

## Client messages

| Type | Purpose |
|---|---|
| `client.register_pairing_offer` | Register a six-digit offer and expiry |
| `client.pairing_decision` | Approve or reject a pending pairing request |
| `auth.connect` | Authenticate as Client using its issued token |
| `heartbeat` | Refresh presence and optionally send telemetry |
| `client.session_decision` | Approve or reject a session request |
| `client.result` | Return a snapshot or command result |

## Controller messages

| Type | Purpose |
|---|---|
| `controller.request_pairing` | Submit a Client's six-digit code |
| `auth.connect` | Authenticate as Controller using its issued token |
| `controller.list_devices` | Retrieve authorized paired Clients |
| `controller.rename_device` | Set a local display alias for an authorized Client |
| `controller.revoke_device` | Revoke the Controller's relationship with a Client |
| `controller.request_snapshot` | Request the current Client snapshot |
| `controller.start_session` | Request a scoped session from a Client |
| `controller.end_session` | End a session owned by the authenticated Controller |
| `controller.command` | Send a supported command, subject to authorization/session checks |

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
| `heartbeat.ack` | Heartbeat acknowledgement |
| `error` | Machine-readable error code and message |

## Session types and states

The Worker recognizes `monitoring`, `screen`, `camera`, `interaction`,
and `files` as session type labels. The current session lifecycle includes
`pending`, `approved`, `rejected`, `failed`, and `ended` states.

Session approval is authorization metadata. It does not create a WebRTC
connection or implement screen capture, camera streaming, file transfer, or
general input control. The current interaction command path requires an
approved `interaction` session.

## Pairing and authorization notes

- Pairing codes are six digits and expire within a maximum ten-minute window.
- The hosted Worker applies attempt limits per Controller and source IP.
- A pairing code is consumed after successful approval.
- Client and Controller receive separate random authentication tokens.
- Controller access is checked against the specific Client relationship.
- Revocation removes the relevant Controller tokens and relationship.

## Errors

Errors use `type: "error"` with a machine-readable `code` and a
human-readable `message`. Pairing failures may be returned through
`controller.pairing_result` with a status such as `failed`,
`rejected`, or `rate_limited`.

## Compatibility rule

Do not add a message type to this document until it exists in the deployed
Worker and corresponding tests. Do not treat the session type list as proof
that the related media or device capability is implemented.
