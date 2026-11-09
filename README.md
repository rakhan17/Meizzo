# MEIZZO

Advanced Android remote device management platform with two independent Android apps:

- **Meizzo Client** (managed device)
- **Meizzo Controller** (operator device)

This repository now contains a **Phase 1 foundation** focused on architecture, security model, repository structure, and a lightweight cloud signaling scaffold with in-memory state.

## Repository Layout

- `apps/client/` — Android app module placeholder for Meizzo Client
- `apps/controller/` — Android app module placeholder for Meizzo Controller
- `shared/` — shared protocol and domain components placeholder
- `services/signaling/` — internet signaling service scaffold (WebSocket, in-memory)
- `docs/` — architecture, pairing, and roadmap documentation

## Implemented Foundation in this phase

- Technology and architecture decisions documented
- Cross-network connectivity model (WebSocket signaling + WebRTC media path with TURN fallback) documented
- Six-digit one-time pairing flow specified and partially implemented in signaling scaffold
- Multi-client session/state model documented
- Security/privacy constraints and Android permission limitations documented
- Update architecture (independent Client/Controller channels via GitHub Releases) documented
- Cost and infrastructure baseline documented

## Current Scope

This foundation does **not** claim full feature completion of camera/screen/remote control flows yet. It establishes the baseline design and signaling building block required for phased implementation.

See:

- `docs/architecture.md`
- `docs/pairing-protocol.md`
- `services/signaling/README.md`
