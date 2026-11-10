# Signaling API reference

The service exposes a WebSocket endpoint at the configured listener and
`GET /healthz` for health checks. Messages are JSON objects with a `type`.

## Client messages

- `client.register_pairing_offer`: `clientId`, six-digit `pairingCode`,
  `expiresAtMs`, `pairingAcceptanceEnabled`, optional `metadata`.
- `client.pairing_decision`: `requestId`, boolean `approved`.
- `auth.connect`: `role=client`, `clientId`, `authToken`.
- `heartbeat`: optional `telemetry`.
- `client.result`: `requestId`, result object, optional error.

## Controller messages

- `controller.request_pairing`: `controllerId`, `pairingCode`.
- `auth.connect`: `role=controller`, `clientId`, `authToken`.
- `controller.list_devices`.
- `controller.request_snapshot`: `clientId`.
- `controller.command`: `clientId`, `command`, optional `parameters`.
- `controller.rename_device`: `clientId`, `alias`.
- `controller.revoke_device`: `clientId`.

## Server events

The server emits `system.hello`, `client.pairing_request`,
`client.pairing_completed`, `controller.pairing_pending`,
`controller.pairing_result`, `auth.connected`, `controller.status`,
`controller.device_list`, `device.status`, `device.result`, `device.renamed`,
`device.revoked`, `heartbeat.ack`, and `error`.

Errors include a machine-readable `code` and user-facing `message`. The
implementation in `services/signaling/src/server.js` is authoritative; this
page intentionally does not document endpoints or fields that are not present
there.
