# MEIZZO V2 architecture

V2 preserves separate Client and Controller applications and separates
identity, signaling, presence, session authorization, and any future media
transport.

## Current control plane

The hosted beta uses a Cloudflare Worker as the WSS entry point and a
`MeizzoSignaling` Durable Object for WebSocket routing and persistent
signaling state. The protocol advertises version 2 and currently implemented
capabilities for pairing, token authentication, presence, telemetry, device
commands, and multi-device routing.

The Worker handles:

- Expiring single-use pairing offers and explicit Client approval.
- Role-specific token authentication and per-Client authorization.
- Heartbeat presence and telemetry forwarding.
- Device list, rename, revoke, snapshot, and supported command messages.
- Authorization-only session request, decision, reconnect restoration, and
  termination.

The Node.js implementation is a local test/development harness with
process-local state. It is not the persistence layer for the hosted service.

## Session authorization boundary

The current protocol supports session types labelled `monitoring`,
`screen`, `camera`, `interaction`, and `files`. A session request creates
an authorization state and requires a Client decision. This does not mean
screen, camera, file, location, or general input capabilities are implemented.

The current interaction command route requires an approved interaction
session. The Client still owns local OS permissions and capability checks.

## Future media plane

A future media plane may use WebRTC, but it must remain separate from
signaling:

1. Client requests the relevant OS permission.
2. Client displays clear, ongoing consent for the capability.
3. Session authorization is checked for the specific Client and Controller.
4. Signaling exchanges the minimum WebRTC negotiation metadata.
5. STUN/TURN establishes the media transport where supported.
6. Client releases capture resources when the session ends or is revoked.

No media transport should be inferred from an authenticated WebSocket.

## Android application boundaries

The long-term target is to separate:

- `presentation`: screens and state-driven navigation.
- `domain`: pairing, device identity, sessions, capabilities, and diagnostics.
- `data`: WebSocket transport, credentials, telemetry, and update clients.
- `platform`: foreground services, MediaProjection, Accessibility, camera,
  location, and Storage Access Framework adapters.

The current code still has meaningful responsibilities in each app's
`MainActivity` and connection services. Refactor incrementally while
preserving package IDs, release signing, and protocol regression tests.

## Future managed enrollment

Managed Mode is not implemented. Enterprise enrollment requires legitimate
Android Enterprise provisioning, organization ownership, policy synchronization,
audit events, and visible device-management state. Device Owner/Profile Owner
status must never be treated as silent consent for sensitive capabilities.

See [architecture audit](architecture-audit.md),
[security model](security-model.md), and
[implementation status](../implementation-status.md).
