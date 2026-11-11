# Meizzo Signaling Service

MEIZZO includes two signaling runtimes:

- **Cloudflare Workers + Durable Objects** for the hosted beta service.
- **Node.js + ws** for local development and the existing regression tests.

The Cloudflare Worker is defined in `services/signaling/cloudflare/worker.js`; the repository-root `wrangler.toml` binds its Durable Object. The Durable Object stores pairing offers, device credentials, controller credentials, pending requests, and session state in SQLite-backed Durable Object storage.

## Deploy with Cloudflare Workers Builds

1. Open [Cloudflare Dashboard](https://dash.cloudflare.com/) and go to **Workers & Pages**.
2. Choose **Create application → Workers → Connect to Git** (wording may vary).
3. Connect GitHub and select `rakhan17/Meizzo`, branch `main`.
4. Set the Worker name to **meizzo-signaling**. It must match `name` in the root `wrangler.toml`.
5. Set **Root directory** to the repository root (leave it blank if blank means repository root).
6. Leave **Build command** blank. The project has no separate compile step.
7. Keep **Preview builds** disabled for the initial setup. Leave **Protect with Cloudflare Access** disabled because the Android and iOS apps must connect directly.
8. For **Deploy command**, use the default `npx wrangler deploy`.
9. Use the automatically generated Cloudflare API token. Do not paste a personal API token into the repository or commit it.
10. Save and deploy. Wait for the build to finish successfully.

Cloudflare's Workers Builds uses the Wrangler deploy command by default and supports GitHub-triggered deployments. The Worker name must match the Wrangler configuration.

## MEIZZO v3 isolated Worker

The v3 development branch uses a separate Worker configuration,
`wrangler.v3.toml`, with Worker name `meizzo-signaling-v3`. It preserves the
stable `meizzo-signaling` deployment and has an independent Durable Object
namespace.

The expected endpoint in this repository is:

`wss://meizzo-signaling-v3.rakhanatanox.workers.dev`

Deploy it through the guarded
[MEIZZO v3 Cloudflare workflow](https://github.com/rakhan17/Meizzo/blob/develop/v3/.github/workflows/deploy-cloudflare-v3.yml)
after configuring `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` in GitHub
Actions repository secrets. Configure the Realtime TURN secrets separately on
the v3 Worker if TURN relay is enabled.

The v3 Worker adds approved-session WebRTC signaling and short-lived TURN
credential delivery. Do not point v3 release builds at the stable Worker.

## Verify the deployment

Open:

`https://meizzo-signaling.<your-subdomain>.workers.dev/healthz`

Expected response:

```json
{
  "status": "ok",
  "service": "meizzo-signaling",
  "runtime": "cloudflare-workers",
  "protocolVersion": 2
}
```

The public signaling endpoint is:

`wss://meizzo-signaling.<your-subdomain>.workers.dev`

Use the exact hostname Cloudflare assigns to the Worker. Do not guess the account subdomain.

## Runtime behavior and limitations

- WebSocket signaling uses a Durable Object singleton so pairing and device state is coordinated across connections.
- Durable Object storage persists application state across isolate restarts. Active WebSocket connections can still disconnect during platform restarts or deployments, and clients must reconnect.
- Presence becomes offline after 45 seconds without a Client heartbeat.
- Pairing offers expire after at most 10 minutes. Pairing attempts are rate-limited per Controller ID and source IP.
- This service handles signaling, pairing, authorization, and device commands only. It does **not** relay screen, camera, or other media streams.
- Cloudflare Free has usage limits. Check the current Workers and Durable Objects quotas in the Cloudflare dashboard; exceeding free quotas can require a paid plan.
- This is a beta deployment. Validate real Client/Controller pairing and reconnection before publishing an Android release against this endpoint.

## Local Node.js server

The Node.js implementation remains available for local testing:

```bash
cd services/signaling
npm ci
npm start
```

Default port: `8080` (override with `PORT`).

Run existing regression tests:

```bash
npm test
```

## Legacy Render configuration

The repository still contains `render.yaml` for reference/rollback. It is not required for the Cloudflare deployment.
