# MEIZZO Architecture

## 1) Technology Selection

### Android
- **Language**: Kotlin (best Android ecosystem support, safety, maintainability)
- **UI**: Jetpack Compose + Material 3 (fast iteration, modern UX)
- **Architecture**: Clean modular architecture + MVVM + use-cases
- **Networking**: Ktor client or OkHttp WebSocket for signaling
- **Media**: WebRTC (screen/camera streaming), CameraX, MediaProjection
- **Background work**: Foreground Services + WorkManager + network callbacks
- **Secure storage**: Android Keystore + EncryptedSharedPreferences

### Backend Signaling
- **Runtime**: Node.js
- **Protocol**: Secure WebSocket (WSS)
- **State**: In-memory only (no persistent cloud DB by default)
- **Reasoning**: Meets low-latency signaling requirement while honoring no persistent server DB requirement

### Build/Release
- **Android build**: Gradle + AGP
- **CI/CD**: GitHub Actions
- **Distribution**: GitHub Releases per app (independent Client/Controller channels)

## 2) Cloud Infrastructure (No Persistent DB)

Required minimal components:
- 1 signaling service instance (or small HA pair)
- TURN service for relay fallback in restricted NAT/mobile carrier networks
- Optional reverse proxy + TLS termination

State handling:
- Pairing offers, connection heartbeats, and session routing exist in memory only
- State expires via TTL and heartbeat timeout
- No permanent storage of camera streams/screen captures/device personal data

Estimated early-stage monthly cost (small pilot):
- Signaling VM/container: ~USD 5–20
- TURN VM/container + bandwidth-dependent relay costs: ~USD 10–100+
- TLS/domain: ~USD 0–15

## 3) Cross-Network Connectivity Design

1. Client and Controller each open WSS to signaling service.
2. Pairing/auth/session messages are routed over signaling.
3. Real-time media uses WebRTC:
   - Prefer P2P via STUN
   - Fallback to TURN relay when direct path fails
4. Heartbeats maintain liveness and status transitions.
5. Reconnect logic maps to explicit device states:
   - Waiting for Connection
   - Connecting
   - Connected
   - Reconnecting
   - Offline
   - Pairing Expired
   - Connection Error

## 4) Android Permission & Capability Limitations

- Runtime permissions can be revoked anytime.
- MediaProjection requires explicit user approval per new session.
- Accessibility must be user-enabled in system settings (cannot be silently enabled).
- Background connectivity depends on OEM battery policies and Android restrictions.
- Capability labels must reflect actual runtime state: Ready / Needs Attention / Not Supported.

## 5) Update Architecture

- Separate app IDs and versions for Client and Controller
- Independent release channels (Stable/Beta)
- GitHub Releases metadata consumed by each app
- APK SHA-256 verification + signing certificate verification before install prompt
- Installation only via Android package installer (no silent install)
- Compatibility matrix check (client/controller minimum supported versions)

## 6) Security Controls

- TLS in transit for signaling
- One-time six-digit pairing code with short expiration
- Code consumption on successful pairing
- Rate limiting on pairing attempts
- Replay protection via request nonce tracking
- Session timeout + revocation support
- No sensitive tokens/codes in logs

## 7) Monorepo Structure

- `apps/client` (Android Client)
- `apps/controller` (Android Controller)
- `shared` (protocol/domain)
- `services/signaling` (cloud signaling service)
- `docs` (architecture, protocol, roadmap)

## 8) Development Roadmap

1. **Phase 1 (implemented)**: Android pairing apps and signaling authorization
2. Phase 2: production TLS deployment, reconnect, and multi-client lifecycle hardening
3. Phase 3: device info/tools (battery/storage/network/alarm/flashlight)
4. Phase 4: screen/camera/location/files with session isolation
5. Phase 5: complete updater pipeline and compatibility gating
6. Phase 6: optimization, compatibility hardening, release readiness
