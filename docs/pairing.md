# Pairing

## User flow

1. Client registers a temporary six-digit offer and expiry.
2. Controller submits the code.
3. Signaling validates the offer, expiry, and attempt limits.
4. Signaling forwards a request to the Client.
5. Client explicitly approves or rejects.
6. On approval, signaling issues separate Client and Controller tokens and
   consumes the pairing code.

Pairing creates an authorized relationship. It does not silently approve
future sessions or grant screen, camera, file, location, or general input
capabilities.

## Protocol messages

| Direction | Message |
|---|---|
| Client to backend | `client.register_pairing_offer` |
| Controller to backend | `controller.request_pairing` |
| Backend to Client | `client.pairing_request` |
| Client to backend | `client.pairing_decision` |
| Backend to peers | `client.pairing_completed`, `controller.pairing_result` |
| Either role to backend | `auth.connect`, `heartbeat` |

Pairing offers expire within a maximum ten-minute window. The hosted Worker
limits attempts per Controller and source IP. Authentication tokens are
role-specific and access is checked against each Client relationship.

## Hosted and local state

The hosted Cloudflare Worker stores signaling state through a Durable Object.
The local Node.js development harness uses in-memory state and loses it on
process restart. These deployment modes have different persistence behavior.

## Session consent

After pairing, Controller may request a scoped session. Client must approve
or reject it separately. An approved interaction session is required before
supported interaction commands are routed. Other session type labels do not
mean screen, camera, files, or location features are already implemented.

## Security properties

- Pairing code is not a permanent credential.
- Successful approval consumes the active offer.
- Pairing attempts are rate-limited.
- Client and Controller receive separate tokens.
- Controller access is scoped to authorized Client relationships.
- Revocation removes the corresponding Controller authorization.

See the [API reference](api-reference.md) and
[security and privacy](security-and-privacy.md).
