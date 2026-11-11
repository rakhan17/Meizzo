# Backend deployment

MEIZZO v2 and v3 use the same hosted Cloudflare signaling service:

`wss://meizzo-signaling.rakhanatanox.workers.dev`

The existing Cloudflare Workers Builds project remains connected to GitHub branch `main` and deploys the root `wrangler.toml` configuration as Worker `meizzo-signaling`. Keep using this one Worker. **Do not create a second beta Worker or a second Workers Builds project.**

The Worker implementation on `main` has been upgraded with v3 signaling capabilities: approved-session WebRTC offer/answer/ICE relay, multi-device authorization, reconnect cleanup, and optional short-lived Cloudflare TURN credential delivery. The v3 Android Client and Controller use the same endpoint.

## Deployment

1. Keep the existing Cloudflare Workers Builds project connected to `rakhan17/Meizzo`, production branch `main`, root directory `/`.
2. Keep its Wrangler deployment configuration pointed at the root `wrangler.toml`.
3. Build command: blank.
4. Deploy command: `npx --yes wrangler@4 deploy --config wrangler.toml`.
5. Preview command: blank unless you deliberately configure a separate preview Worker. Do not point previews at production.
6. Use the existing automatically generated Cloudflare Workers Builds API token. Do not create or paste a personal API token into the repository.

The `develop/v3` branch builds and tests the v3 apps against the shared endpoint. Worker-side changes from v3 must be merged into `main` to deploy through the existing Cloudflare project. This keeps one production signaling endpoint.

## Verify

Open `https://meizzo-signaling.rakhanatanox.workers.dev/healthz`. Confirm `status: "ok"`, `service: "meizzo-signaling"`, and `runtime: "cloudflare-workers"`. Then test WSS, Client/Controller pairing, approved-session media signaling, and reconnect behavior.

## Optional Cloudflare Realtime TURN relay

The Worker requests short-lived Cloudflare Realtime TURN credentials only after an approved session. Store the TURN key ID as Worker secret `REALTIME_TURN_TOKEN_ID` and its secret as `REALTIME_TURN_TOKEN_SECRET`. These are TURN credentials, not deployment API credentials. If absent or unavailable, the Worker falls back to STUN-only connectivity.

## Local Node.js development harness

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

The Node.js implementation is for local development and tests. It uses process-local Maps, so data is lost when the process exits or restarts.

## Security and operations

- Use WSS in published builds and keep normal TLS certificate validation.
- Never commit Cloudflare API tokens, keystore passwords, or release secrets.
- Keep Worker credentials and Realtime TURN key secrets in Cloudflare's secret store. Never expose them to Client or Controller APKs.
- Avoid logging WebSocket message bodies, pairing codes, and auth tokens.
