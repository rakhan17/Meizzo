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

The Node.js and Durable Object tests cover pairing approval, one-time code
consumption, authentication, multi-device isolation, revocation, presence,
session restart after termination, Client reconnect cleanup, participant-bound
media signaling, Client-initiated session termination, STUN fallback, and
short-lived TURN credential delivery. These tests do not prove live network
connectivity or Cloudflare production deployment.

## Android CI

Android CI builds Client and Controller as separate debug APKs and validates
their package identifiers. The v2.5.0 stable release workflow builds signed
release variants. The v3 branch has not published a release APK yet.

- [Current v3 Android and signaling CI](https://github.com/rakhan17/Meizzo/actions?query=branch%3Adevelop%2Fv3)
- [Stable v2.5.0 release](https://github.com/rakhan17/Meizzo/releases/tag/v2.5.0)

## iOS CI

The iOS workflow builds the Client and Controller for iOS Simulator and runs
Simulator smoke checks. This is not equivalent to signing, installing, or
testing on a physical iPhone.

- [iOS CI run](https://github.com/rakhan17/Meizzo/actions/runs/42946246991)

## Cloudflare TURN configuration

The existing `meizzo-signaling` Worker can issue short-lived Cloudflare TURN credentials without a VPS. Follow [the setup guide](cloudflare-turn-setup.md) to configure its two encrypted Worker secrets. Check `/healthz` for `turnConfigured`; a true value confirms presence only, not credential validity or real-world relay reachability.

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
13. Verify the remote alarm rings continuously beyond 30 seconds, stops on
    remote OFF, and can be stopped locally from its foreground notification.
14. Confirm Controller status changes only after a Client command result arrives.
15. Test revoke and confirm access is removed.
16. Test notification, background, battery optimization, reboot, and network
    interruption behavior. Remove the Client Activity from Recents and verify
    the service-owned WSS connection remains active; issue a remote alarm command
    and confirm the service handles it without the Activity. Reopen the Activity
    and confirm queued signaling events are replayed.
17. Test updater checksum/package/signature checks using a valid release.

Record exact results. Never record tokens, passwords, pairing codes, or private
endpoint credentials in test reports.

## v3 remote media and file-transfer acceptance

Run these checks on real Android devices after the v3 Worker has been deployed
and the TURN secrets have been configured. CI compilation alone is not runtime
acceptance.

1. **Live screen:** approve the MEIZZO session, approve Android's MediaProjection
   prompt, confirm the Controller renders the correct orientation, and verify
   the stream stops when either participant ends the session.
2. **Remote camera:** approve the camera session and Android CAMERA permission;
   verify the rear camera stream and test permission denial.
3. **Remote input:** approve the separate interaction session, enable the
   AccessibilityService, then test taps and swipes at the center and edges of
   portrait and landscape video. Confirm touches in letterbox bars do nothing.
4. **Remote lock:** test the lock action on Android 9+ and confirm it is
   unavailable when Accessibility access is disabled.
5. **File transfer:** send files in both directions, verify SHA-256 completion,
   test unknown-size document providers, reject files over 50 MiB, cancel
   destination selection, and confirm partial files are removed after failure.
   Interrupt a files session after several acknowledged chunks, establish a new
   approved files session, and verify both peers resume from the last acknowledged
   offset without duplicated or missing bytes. Repeat after force-closing one app
   and confirm resume is offered only when its persisted SAF grant remains valid.
6. **Network traversal:** test same Wi-Fi, different Wi-Fi networks, mobile
   data, restrictive NAT, and Wi-Fi/mobile handover. Confirm TURN is used when
   direct ICE fails.
7. **Session recovery:** stop the interaction session and request it again;
   end screen/camera/files sessions from each side; reconnect Client and
   Controller and verify no stale approval or media state remains.

## Release acceptance boundary

The v2.5.0 stable release is published. v3 remains a development branch until
the remote-media acceptance matrix above passes on physical devices and
cross-network conditions. Physical-device runtime acceptance, OEM background
behavior, and complete end-to-end multi-device validation must be recorded
separately.

## Documentation checks

```bash
python scripts/check-doc-links.py
mkdocs build --strict
```

Documentation CI runs both checks for documentation changes.
