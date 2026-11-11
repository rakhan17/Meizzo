# Meizzo Signaling Service

MEIZZO includes two signaling runtimes:

- **Cloudflare Workers + Durable Objects** for hosted beta deployments.
- **Node.js + ws** for local development and regression tests.

The Cloudflare Worker entry point is `services/signaling/cloudflare/worker.js`. The root `wrangler.toml` defines the stable Worker and its Durable Object binding. `wrangler.v3.toml` defines an isolated v3 Worker with its own Durable Object namespace.

## Stable deployment

The stable Cloudflare Workers Builds project is connected to the repository's `main` branch and deploys the root `wrangler.toml` configuration as `meizzo-signaling`.

Keep that project on `main`. Do not change its branch or Wrangler config to v3.

## MEIZZO v3 deployment with Cloudflare Workers Builds

The v3 branch uses a shared beta Worker named `meizzo-signaling-beta`. Deploy it using a **second Cloudflare Workers Builds project**, connected to the same GitHub repository but configured for branch `develop/v3`.

One-time setup in Cloudflare Dashboard:

1. Open [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages**.
2. Choose **Create application → Workers → Connect to Git**.
3. Select repository `rakhan17/Meizzo`.
4. Set the production branch to `develop/v3`.
5. Set the Worker name to `meizzo-signaling-beta` if Cloudflare asks for one. The authoritative name is already in `wrangler.v3.toml`.
6. Keep the repository root as the root directory.
7. Leave the build command blank.
8. Set the deploy command to:

   ```bash
   npx --yes wrangler@4 deploy --config wrangler.v3.toml
   ```

9. Set Preview command to `npx --yes wrangler@4 preview --config wrangler.v3.toml` and leave Build command blank.
10. Use Cloudflare Workers Builds' automatically generated API token. Do not create GitHub Actions secrets for Cloudflare and never commit a personal API token.
10. Save and deploy.

After this one-time setup, pushes to `develop/v3` automatically build and deploy only the v3 Worker. The existing `main` → `meizzo-signaling` project remains unchanged. GitHub Actions separately runs the Worker dry-run and regression tests.

Expected v3 endpoint for the existing account subdomain:

`wss://meizzo-signaling-beta.rakhanatanox.workers.dev`

Treat that hostname as provisional until Cloudflare confirms the deployment URL in its dashboard. Verify `/healthz` and a WebSocket handshake before using it for real sessions or release builds.

The v3 Worker adds approved-session WebRTC signaling and short-lived TURN credential delivery. If TURN relay is enabled, configure `REALTIME_TURN_TOKEN_ID` and `REALTIME_TURN_TOKEN_SECRET` as secrets on the v3 Worker in Cloudflare. These are TURN credentials, not Cloudflare deployment credentials.

## Verify the deployment

Open:

`https://meizzo-signaling-beta.<your-subdomain>.workers.dev/healthz`

Use the exact hostname Cloudflare assigns. The expected health response has `status: "ok"`, `service: "meizzo-signaling-beta"`, and `runtime: "cloudflare-workers"`.

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
