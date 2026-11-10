# MEIZZO V2 security model

## Trust boundaries

- A pairing code is an invitation, not an authorization token.
- Client approval creates the initial relationship.
- Every later request is authorized against the specific Client relationship.
- A WebSocket connection does not authorize media, camera, location, files, or
  input by itself.
- Android OS consent remains authoritative for sensitive capabilities.

## Required controls

- WSS/HTTPS in production with normal certificate validation.
- Expiring, single-use pairing offers and rate limiting.
- Keystore-backed Client and Controller credentials.
- Per-device authorization and revocation.
- Session-scoped capability grants.
- Minimal logs without tokens, screen contents, or personal data.
- Durable audit events for sensitive session transitions.

The current beta implements the first four controls in prototype form but
stores server state in memory. Durable storage, token rotation, and production
operational controls are prerequisites for V2 production deployment.

Session requests are now scoped to one authorized Client and Controller. The
Client must approve a request before it reaches `approved`; ending a session
does not grant permission to start another session silently. Media and input
capabilities still require their own Android permission and consent flows.
