# Meizzo Signaling Service

This service provides WebSocket signaling with expiring in-memory state only. Put it behind a TLS reverse proxy for production; the service deliberately does not persist device identity or media.

Implemented foundation behaviors:
- client pairing offer registration (`client.register_pairing_offer`)
- controller pairing request (`controller.request_pairing`)
- pairing attempt rate limiting
- offer expiration handling
- one-time offer consumption on approval
- explicit client approval path (`client.pairing_decision`)
- post-pairing token authentication (`auth.connect`)
- heartbeat and online/offline status
- controller device listing and revocation
- `GET /healthz` health check

## Run

```bash
cd services/signaling
npm install
npm run start
```

Default port: `8080` (set `PORT` env var to override)

For production, set `PORT` behind a WSS-capable reverse proxy and restrict origins at the proxy. Use a managed TURN service for WebRTC once media sessions are enabled. The current service is a signaling and authorization component, not a media relay.
