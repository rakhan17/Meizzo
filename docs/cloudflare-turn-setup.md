# Cloudflare TURN setup for MEIZZO

MEIZZO does not need a VPS or a second signaling service for WebRTC relay.
The existing `meizzo-signaling` Cloudflare Worker requests short-lived TURN
credentials from Cloudflare Realtime and forwards them only to the two
participants of an approved media session.

## One-time Cloudflare setup

1. Open the Cloudflare dashboard and select the account that owns
   `meizzo-signaling`.
2. Open **Realtime / TURN** and create a TURN key. Keep its Token ID and
   associated API token private.
3. Create or use an API token with the Cloudflare Realtime TURN permission
   required to generate TURN credentials. Do not use a broad account-wide
   token if a narrowly scoped permission is available.
4. Open **Workers & Pages → meizzo-signaling → Settings → Variables and
   Secrets** in the existing Worker.
5. Add these as encrypted secrets, not plain variables:
   - `REALTIME_TURN_TOKEN_ID`: the TURN key ID from step 2.
   - `REALTIME_TURN_TOKEN_SECRET`: the API token used to generate credentials.
6. Save and deploy the existing Worker. Do not create another Worker, another
   Workers Builds project, or a VPS.

For repeatable deployments, the repository includes a guarded GitHub Actions
workflow. Add `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` as encrypted
GitHub Actions secrets (or secrets in the `production` environment), then open
**Actions → MEIZZO Cloudflare Worker → Run workflow** and set `deploy` to
`true`. The workflow validates the bundle first, deploys the existing Worker
from `wrangler.toml`, then checks `/healthz`. A normal push or pull request only
runs the dry-run validation and never deploys production.

The Worker uses the secrets server-side to request credentials with a
24-hour TTL. The long-lived API token and TURN key never go into either APK,
the repository, or the Worker health response.

## Cost and free usage

Cloudflare Realtime TURN currently includes **1,000 GB of monthly egress at
no charge**. Usage above the included allowance is billed at **US$0.05 per
GB**. The allowance is shared with Cloudflare Realtime SFU, not a separate
1,000 GB pool for each service. MEIZZO uses point-to-point TURN only and does
not require SFU. Cloudflare's STUN service remains free and unlimited.

Keep an eye on Realtime usage in the Cloudflare dashboard. If staying strictly
within a zero-cost budget is mandatory, leave TURN secrets unset and use the
free STUN-only mode; direct connectivity then depends on both networks' NAT
and firewall behavior.

Pricing is subject to change. See [Cloudflare Realtime TURN FAQ](https://developers.cloudflare.com/realtime/turn/faq/) and [Realtime pricing](https://developers.cloudflare.com/realtime/sfu/platform/pricing/).

## Verify

Open:

`https://meizzo-signaling.rakhanatanox.workers.dev/healthz`

Expected fields:

- `status: "ok"`
- `mediaTransport: "webrtc"`
- `turnConfigured: true`

The `turnConfigured` field only confirms that both secret values are present.
It does not prove that the API token is valid or that a TURN relay can be
reached from a particular network. Confirm relay behavior with a real
cross-network WebRTC test and inspect the selected ICE candidate pair.

If the secrets are absent or Cloudflare's credential endpoint is unavailable,
the Worker falls back to Cloudflare STUN. Direct connections may still work,
but restrictive NATs, carrier-grade NAT, or firewalls can prevent a session
from connecting. The Worker health endpoint reports `turnConfigured: false`
in this mode.

## Security notes

- Never paste TURN API tokens or TURN key secrets into GitHub issues, chat,
  source files, Android build configuration, or app preferences.
- If a secret is exposed, revoke it in Cloudflare and replace the Worker
  secret immediately.
- TURN credentials sent to clients are short-lived; they are not the
  long-lived API credentials.
- Cloudflare TURN is a relay for WebRTC traffic. It is not RustDesk's
  `hbbs`/`hbbr` server and does not implement the RustDesk protocol.

References:

- [Cloudflare TURN credential generation](https://developers.cloudflare.com/realtime/turn/generate-credentials/)
- [Cloudflare Realtime overview](https://developers.cloudflare.com/realtime/)
