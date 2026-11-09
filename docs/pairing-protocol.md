# MEIZZO Six-Digit Pairing Protocol (Foundation)

## Objectives
- Simple UX: controller enters a 6-digit code
- Strong security: one-time code, short TTL, replay protection, rate limits
- No persistent cloud DB requirement

## Entities
- **Client**: managed Android device
- **Controller**: operator Android device
- **Signaling Service**: in-memory session coordinator

## Flow
1. Client generates cryptographically secure 6-digit code on first install identity creation.
2. Client announces active pairing offer to signaling service with expiry and acceptance-enabled status.
3. Controller submits code to signaling service.
4. Signaling validates format, rate limits, TTL, and active offer.
5. Signaling forwards request to matched Client for explicit approval.
6. Client accepts/rejects.
7. On success, signaling consumes code and completes association response.
8. Controller stores resulting paired identity/credentials in secure local storage.

## Security Properties
- Code never acts as permanent credential
- One-time consumption after success
- Brute-force mitigation with attempt windows
- Replay resistance via nonce/request tracking
- Explicit client-side approval required

## Required Persistent Data (local device only)
- Client identity key material
- Controller paired device records
- Custom device names and pairing timestamps

## Non-Persistent Data (cloud memory only)
- Active pairing offers
- Online session routes
- Heartbeat and temporary request state
