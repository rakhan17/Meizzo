# Backend deployment

MEIZZO currently has two signaling implementations with different persistence
and operational properties. Do not treat them as interchangeable.

## Hosted Cloudflare Worker

The published Android beta is configured for the hosted WSS endpoint:

`wss://meizzo-signaling.rakhanatanox.workers.dev`

The deployment configuration is:

- Root `wrangler.toml`: Worker entry point and Durable Object binding.
- `services/signaling/cloudflare/worker.js`: HTTP health endpoint, WebSocket
  upgrade, Durable Object class, protocol handling.
- Durable Object class: `MeizzoSignaling`.
- Durable Object state: stored through the Durable Object storage API.

The Worker exposes `GET /healthz` and WebSocket upgrades at `/` and
`/ws`. The Worker validation workflow performs a Wrangler dry-run only.
Production deployment is available through the manual
[`Deploy MEIZZO Cloudflare Worker`](https://github.com/rakhan17/Meizzo/blob/develop/v3/.github/workflows/deploy-cloudflare.yml)
workflow. It requires the GitHub Actions secrets `CLOUDFLARE_API_TOKEN` and
`CLOUDFLARE_ACCOUNT_ID`; TURN support additionally requires
`REALTIME_TURN_TOKEN_ID` and `REALTIME_TURN_TOKEN_SECRET`. Run it against
`develop/v3` only after reviewing the deployment diff.

### Deployment checklist

1. Confirm the Cloudflare account and Worker name.
2. Confirm the Durable Object binding and migration in `wrangler.toml`.
3. Validate with `npx wrangler@4 deploy --dry-run --config wrangler.toml`.
4. Deploy using the authorized Cloudflare workflow/credentials.
5. Verify `/healthz` and a WSS handshake.
6. Run pairing, authentication, reconnect, multi-device isolation, session,
   and revocation smoke tests.
7. Monitor Durable Object errors, limits, latency, and storage growth.

The current beta uses a single named Durable Object for the signaling
namespace. Review capacity, failure recovery, abuse controls, retention, and
operational monitoring before production use.

## Optional Cloudflare Realtime TURN relay

The Android v3 WebRTC implementation requests session-scoped ICE server
configuration after Client approval. The Worker always returns Cloudflare STUN
and, when TURN credentials are configured, requests short-lived Cloudflare
Realtime TURN credentials and sends them only to the two approved session
participants. The long-lived TURN key never leaves the Worker.

### Configure TURN

1. In the Cloudflare Dashboard, open **Realtime → TURN Server** and create a
   TURN key. Record its key ID and secret securely.
2. Store the key ID as a Worker secret:
   `npx wrangler secret put REALTIME_TURN_TOKEN_ID`
3. Store the TURN key API secret as a Worker secret:
   `npx wrangler secret put REALTIME_TURN_TOKEN_SECRET`
4. Deploy the Worker with the authorized Cloudflare credentials.
5. Approve a screen, camera, or file session and verify that the Worker returns
   short-lived ICE credentials to both participants.

The Worker requests a 24-hour credential lifetime. If the two secrets are not
configured or Cloudflare's credential API is unavailable, MEIZZO falls back to
STUN-only connectivity. TURN credentials must never be hardcoded in Android
build configuration or committed to the repository.

Cloudflare Realtime TURN is a metered relay service. Check current account
pricing and usage limits before enabling it for public users.

## Local Node.js development harness

The Node.js implementation is for local development and tests. It uses
process-local Maps, so offers, device relationships, sessions, and telemetry
are lost when the process exits or restarts.

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

The default listener is port 8080 and the health route is `/healthz`.
For Android emulator development, configure the debug WebSocket URL to the
host address reachable from that emulator. Do not use emulator-only addresses
in a public release build.

## Security and operations

- Use WSS in published builds and keep normal TLS certificate validation.
- Never commit Cloudflare API tokens, keystore passwords, or release secrets.
- Keep Worker credentials and Realtime TURN key secrets in the provider's
  secret store. Never expose them to Client or Controller APKs.
- Avoid logging WebSocket message bodies, pairing codes, and auth tokens.
- Define retention, token rotation, incident response, and access policies.
- Add independent security review and abuse monitoring before production use.

See [architecture](architecture.md), [security and privacy](security-and-privacy.md),
and [testing](testing.md).
