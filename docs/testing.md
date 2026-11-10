# Testing

## Verified automated commands

Backend:

```bash
cd services/signaling
npm ci
npm run check
npm test
```

The tests cover pairing approval, one-time codes, authentication,
multi-device isolation, offline/reconnect, heartbeat telemetry, and stale
connection behavior.

Android CI builds Client and Controller separately and validates package IDs.
iOS CI builds each target for `iphonesimulator`, installs both apps on a
Simulator, and launches the expected bundle IDs.

## Runtime limitations

Simulator smoke testing proves install and launch only; it does not prove live
pairing through two UI instances. No physical iPhone/iPad or three-instance
BlueStacks runtime is available in this repository session. Use
`scripts/bluestacks-test.sh` and follow the manual matrix below.

## Three-instance matrix

1. Install the same debug build on Controller, Client A, and Client B.
2. Start the backend and confirm `/healthz`.
3. Pair A and B, approve both requests, and verify both are online.
4. Compare telemetry identity, battery, network, and last update for each.
5. Disconnect A; confirm B remains online.
6. Reconnect A and restart Controller; verify all states recover.
7. Test background/foreground and backend interruption.
8. Use the Android update checker and confirm certificate-preserving install.

Record device serials, app versions, endpoint type, and exact results. Never
record tokens or pairing codes.
