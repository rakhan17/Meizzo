# Testing and release validation

MEIZZO uses automated signaling tests, independent Android builds, iOS
Simulator smoke tests, Worker validation, documentation validation, and
manual device acceptance. Each proves a different layer.

## Signaling tests

```bash
cd services/signaling
npm ci
npm run check
npm test
```

The Node.js test suite covers pairing approval, one-time code consumption,
authentication, multi-device isolation, revocation, presence, reconnect, and
related session flows. These tests exercise the local Node harness and do not
by themselves prove Cloudflare production behavior.

## Android CI

Android CI builds Client and Controller as separate debug APKs and validates
their package identifiers. The beta.5 release workflow additionally builds
release variants, signs both APKs, verifies signatures, checks package/version
metadata, and publishes checksums and the updater manifest.

- [Android CI run](https://github.com/rakhan17/Meizzo/actions/runs/42946246894)
- [Beta.5 release run](https://github.com/rakhan17/Meizzo/actions/runs/42948592178)

## iOS CI

The iOS workflow builds the Client and Controller for iOS Simulator and runs
Simulator smoke checks. This is not equivalent to signing, installing, or
testing on a physical iPhone.

- [iOS CI run](https://github.com/rakhan17/Meizzo/actions/runs/42946246991)

## Worker validation

The Cloudflare workflow runs Wrangler validation/dry-run for the Worker
configuration. A dry-run does not deploy the Worker or prove live service
availability. Follow it with health, WSS, pairing, reconnect, and authorization
smoke tests against the deployed endpoint.

## Manual Android acceptance matrix

Use two or more Android environments. For multi-device isolation, use one
Controller and at least two Clients.

1. Install the exact Client and Controller release APKs.
2. Record app version, Android version, device model, and endpoint.
3. Confirm the service connects over WSS.
4. Pair Client A and approve on Client A.
5. Pair Client B and approve on Client B.
6. Confirm both devices appear independently.
7. Verify telemetry identity and last-update timestamps.
8. Disconnect Client A and confirm Client B remains online.
9. Reconnect Client A and verify presence restoration.
10. Restart Controller and verify paired devices re-authenticate.
11. Test session pending, approve, reject, and end transitions.
12. Verify an interaction command is rejected without an approved interaction
    session.
13. Test revoke and confirm access is removed.
14. Test notification, background, battery optimization, reboot, and network
    interruption behavior.
15. Test updater checksum/package/signature checks using a valid release.

Record exact results. Never record tokens, passwords, pairing codes, or private
endpoint credentials in test reports.

## Release acceptance boundary

The beta.5 release workflow passed and published signed APKs. This proves
release automation and artifact validation, not universal device
compatibility. Physical-device runtime acceptance, OEM background behavior,
and complete end-to-end multi-device validation must be recorded separately.

## Documentation checks

```bash
python scripts/check-doc-links.py
mkdocs build --strict
```

Documentation CI runs both checks for documentation changes.
