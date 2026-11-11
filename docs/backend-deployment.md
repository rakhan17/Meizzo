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
`/ws`. The Worker validation workflow performs a Wrangler dry-run. Deployment
is handled by the configured Cloudflare workflow and credentials.

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
- Keep Worker credentials in GitHub Actions Secrets or the provider's secret
  store.
- Avoid logging WebSocket message bodies, pairing codes, and auth tokens.
- Define retention, token rotation, incident response, and access policies.
- Add independent security review and abuse monitoring before production use.

See [architecture](architecture.md), [security and privacy](security-and-privacy.md),
and [testing](testing.md).
