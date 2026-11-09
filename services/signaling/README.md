# Meizzo Signaling Service (Foundation Scaffold)

This service provides secure WebSocket signaling with in-memory state only.

Implemented foundation behaviors:
- client pairing offer registration (`client.register_pairing_offer`)
- controller pairing request (`controller.request_pairing`)
- pairing attempt rate limiting
- offer expiration handling
- one-time offer consumption on approval
- explicit client approval path (`client.pairing_decision`)

## Run

```bash
cd services/signaling
npm install
npm run start
```

Default port: `8080` (set `PORT` env var to override)
