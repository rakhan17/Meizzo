# Meizzo Signaling Service

This service provides WebSocket signaling with expiring in-memory state only. Render terminates public TLS and forwards traffic to this Node.js service; Android and iOS clients must connect using the service's `wss://` URL.

Implemented foundation behaviors:
- client pairing offer registration (`client.register_pairing_offer`)
- controller pairing request (`controller.request_pairing`)
- pairing attempt rate limiting
- offer expiration handling
- one-time offer consumption on approval
- explicit client approval path (`client.pairing_decision`)
- post-pairing token authentication (`auth.connect`)
- heartbeat and online/offline status
- authoritative presence timeout: 45 seconds without a client heartbeat
- client heartbeat interval: 15 seconds
- real-time per-device telemetry updates carried by authenticated heartbeats
- controller device listing and revocation
- `GET /healthz` health check

## Deploy to Render

The repository-root `render.yaml` defines a free Node.js web service:

- Root directory: `services/signaling`
- Build command: `npm ci`
- Start command: `npm start`
- Health check: `/healthz`
- Region: Singapore

1. Push the repository to GitHub.
2. Open the [Render Dashboard](https://dashboard.render.com/).
3. Choose **New → Blueprint** and connect this repository.
4. Review the `meizzo-signaling` service from `render.yaml`, then apply the Blueprint.
5. Wait for the first deploy to become **Live**.
6. Test `https://YOUR-SERVICE.onrender.com/healthz`. It should return HTTP 200 and JSON similar to `{"status":"ok","uptimeSeconds":12}`.
7. Use `wss://YOUR-SERVICE.onrender.com` as the signaling URL in the Android release configuration. Do not use `ws://` for public releases.

## Keep the Render Free instance warm with UptimeRobot

Render Free web services spin down after 15 minutes without inbound HTTP requests or WebSocket messages. An external monitor can send a lightweight health request more frequently:

1. Create an account at [UptimeRobot](https://uptimerobot.com/).
2. Add a monitor with type **HTTP(s)**.
3. Set the URL to `https://YOUR-SERVICE.onrender.com/healthz`.
4. Choose a **5-minute** monitoring interval and save it.
5. Confirm the monitor receives a successful HTTP response.

This is a best-effort keep-warm workaround, not an uptime guarantee or a substitute for a paid always-on instance. A continuously running free instance uses approximately 720 hours in a 30-day month, close to Render's 750 included free instance hours per workspace. Render can still restart free instances at any time.

## Important service limitations

- Pairing credentials, device presence, and sessions currently live in process memory. A restart, redeploy, or spin-down loses that state, so devices may need to pair again.
- This service handles signaling and authorization only. It is not a media relay.
- The current service does not persist device identity or media.
- The free Render plan is suitable for beta/testing, not production-critical availability.
- Add a managed TURN service when WebRTC media sessions are enabled.

## Run locally

```bash
cd services/signaling
npm ci
npm start
```

Default port: `8080` (set `PORT` env var to override).
