# Backend deployment

MEIZZO has two signaling implementations with different persistence and operational properties. Do not treat them as interchangeable.

## Stable Cloudflare Worker

The stable Android beta endpoint is:

`wss://meizzo-signaling.rakhanatanox.workers.dev`

The stable Cloudflare Workers Builds project is connected to GitHub branch `main` and deploys the root `wrangler.toml` configuration as Worker `meizzo-signaling`.

Keep this project connected to `main`. The v3 deployment must use a separate Cloudflare Workers Builds project and must not replace this Worker.

## Isolated MEIZZO v3 Worker

The v3 development branch has a separate Wrangler configuration, `wrangler.v3.toml`, and a separate Worker name, `meizzo-signaling-v3`. It has its own Durable Object namespace. This separation preserves the stable Worker while v3 adds participant-bound WebRTC signaling, ICE-server delivery, and additional session/reconnect checks.

Expected v3 endpoint for the existing Cloudflare account subdomain:

`wss://meizzo-signaling-v3.rakhanatanox.workers.dev`

### One-time Cloudflare Workers Builds setup

Create a second Workers Builds project in the Cloudflare Dashboard:

1. Open [Cloudflare Dashboard](https://dash.cloudflare.com/) → **Workers & Pages** → **Create application** → **Workers** → **Connect to Git**.
2. Select GitHub repository `rakhan17/Meizzo`.
3. Set the production branch to `develop/v3`.
4. Keep the repository root as the root directory.
5. Leave the build command blank.
6. Set the deploy command to:

   ```bash
   npx wrangler deploy --config wrangler.v3.toml
   ```

7. Use the automatically generated Cloudflare API token provided by Workers Builds.
8. Save and deploy.

After setup, each push to `develop/v3` automatically deploys the isolated v3 Worker. GitHub Actions runs the v3 Wrangler dry-run and tests independently. **No `CLOUDFLARE_API_TOKEN` or `CLOUDFLARE_ACCOUNT_ID` GitHub Actions secrets are needed for this deployment path.** Do not paste a personal Cloudflare API token into the repository.

Cloudflare's dashboard is authoritative for the final Worker hostname. Confirm the hostname there rather than assuming the account subdomain.

### Verify

1. Open `https://<actual-v3-worker-hostname>/healthz`.
2. Confirm the response reports `status: "ok"` and `service: "meizzo-signaling-v3"`.
3. Test a WSS connection and a real Client/Controller pairing.
4. Test approved-session offer/answer/ICE signaling and reconnect behavior.
5. Configure TURN separately if required.

### Optional Cloudflare Realtime TURN relay

The Android v3 WebRTC implementation requests session-scoped ICE server configuration after Client approval. The Worker returns Cloudflare STUN and, when TURN credentials are configured, requests short-lived Cloudflare Realtime TURN credentials and sends them only to the two approved session participants. The long-lived TURN key never leaves the Worker.

To configure TURN:

1. In Cloudflare Dashboard, open **Realtime → TURN Server** and create a TURN key.
2. Store its key ID as Worker secret `REALTIME_TURN_TOKEN_ID`.
3. Store its secret as Worker secret `REALTIME_TURN_TOKEN_SECRET`.
4. Apply both secrets to the **v3 Worker**, not the stable Worker.
5. Deploy the v3 Worker and verify the Worker returns short-lived ICE credentials to both approved participants.

If TURN secrets are absent or the credential API is unavailable, MEIZZO falls back to STUN-only connectivity. TURN credentials must never be hardcoded in Android builds or committed to the repository. Cloudflare Realtime TURN is metered, so check current account pricing and usage limits.

## Local Node.js development harness

The Node.js implementation is for local development and tests. It uses process-local Maps, so offers, device relationships, sessions, and telemetry are lost when the process exits or restarts.

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

The default listener is port 8080 and the health route is `/healthz`. For Android emulator development, configure the debug WebSocket URL to the host address reachable from that emulator. Do not use emulator-only addresses in public release builds.

## Security and operations

- Use WSS in published builds and keep normal TLS certificate validation.
- Never commit Cloudflare API tokens, keystore passwords, or release secrets.
- Keep Worker credentials and Realtime TURN key secrets in Cloudflare's secret store. Never expose them to Client or Controller APKs.
- Avoid logging WebSocket message bodies, pairing codes, and auth tokens.
- Define retention, token rotation, incident response, and access policies.
- Add independent security review and abuse monitoring before production use.

See [architecture](architecture.md), [security and privacy](security-and-privacy.md), and [testing](testing.md).
