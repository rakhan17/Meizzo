# V2 deployment

## Current verified deployment

The backend runs locally with:

```bash
cd services/signaling
npm ci
PORT=8080 npm start
```

It must be placed behind a TLS-capable reverse proxy before release clients
use it. The Android release workflow rejects missing, local, or placeholder
WSS endpoints and requires a protected signing keystore.

## Production prerequisites

- Hosted process with health monitoring and resource limits.
- WSS/HTTPS termination with a managed certificate.
- Durable identity, device, token, and session storage.
- Secret management and key rotation.
- TURN service for media.
- Privacy-aware logging and retention.
- Backup, incident response, and revocation procedures.

GitHub Actions builds and publishes artifacts; it is not backend hosting.
