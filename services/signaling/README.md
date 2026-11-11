# Meizzo Signaling Service

MEIZZO includes two signaling runtimes:

- **Cloudflare Workers + Durable Objects** for hosted beta deployments.
- **Node.js + ws** for local development and regression tests.

The Cloudflare Worker entry point is `services/signaling/cloudflare/worker.js`. The root `wrangler.toml` defines the stable Worker and its Durable Object binding. The v3 app also uses the same stable Worker endpoint; there is no separate beta Worker.

## Stable deployment

The stable Cloudflare Workers Builds project is connected to the repository's `main` branch and deploys the root `wrangler.toml` configuration as `meizzo-signaling`.

Keep that project on `main`. Do not change its branch or Wrangler config to v3.

## MEIZZO v3 signaling

The v3 Client and Controller use the existing `meizzo-signaling` Worker. Do not create a second Workers Builds project or deploy a separate Worker. Keep the current Cloudflare project connected to `main` and deploy the root `wrangler.toml`.

Verify the endpoint at:

`https://meizzo-signaling.rakhanatanox.workers.dev/healthz`

## Runtime behavior and limitations

- WebSocket signaling uses a Durable Object singleton so pairing and device state is coordinated across connections.
- Durable Object storage persists application state across isolate restarts. Active WebSocket connections can still disconnect during platform restarts or deployments, and clients must reconnect.
- Presence becomes offline after 45 seconds without a Client heartbeat.
- Pairing offers expire after at most 10 minutes. Pairing attempts are rate-limited per Controller ID and source IP.
- This service handles signaling, pairing, authorization, and device commands only. It does **not** relay screen, camera, or other media streams.
- Cloudflare plan usage limits apply. Check current Workers and Durable Objects quotas in the Cloudflare dashboard.
- This is a beta deployment. Validate real Client/Controller pairing and reconnection before publishing an Android release against this endpoint.

## Local Node.js server

The Node.js implementation remains available for local testing:

```bash
cd services/signaling
npm ci
npm start
```

Default port: `8080` (override with `PORT`).

Run regression tests:

```bash
npm test
```

## Legacy Render configuration

The repository still contains `render.yaml` for reference/rollback. It is not required for Cloudflare deployment.
