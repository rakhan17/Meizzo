# Backend deployment

The signaling service is a Node.js process using the `ws` dependency. It
listens on `PORT` (default `8080`) and exposes `/healthz`. Local state includes
pairing offers, tokens, sessions, presence, and telemetry; it is lost on
restart.

## Local

```bash
cd services/signaling
npm ci
npm run check
npm test
PORT=8080 npm start
```

## Production requirements

Put the service behind a TLS reverse proxy or managed WebSocket endpoint and
configure the apps with `wss://`. Restrict access to health and WebSocket
traffic, set process/resource limits, and ensure access logs exclude message
bodies and credentials. Add persistent state and a deliberate retention model
before treating the service as production-ready.

The repository does not include a hosted production endpoint, TURN service,
database, or deployment manifest. Do not infer one from the local service.
