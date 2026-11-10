# V2 networking

The control plane uses WebSocket signaling. Production requires a reachable
WSS endpoint with TLS termination and normal certificate validation. Local
debug builds may use an explicitly supplied `ws://` endpoint; `10.0.2.2` is
not a universal BlueStacks Air gateway.

The current signaling service listens on `PORT`, exposes `/healthz`, and keeps
state in memory. It is suitable for local development and regression tests,
not horizontal production deployment.

The future media plane requires WebRTC, STUN, and TURN. TURN is necessary for
networks where direct peer connectivity fails. No hosted signaling, STUN, or
TURN service is included in this repository.
