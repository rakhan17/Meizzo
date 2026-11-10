# Troubleshooting

## `CLEARTEXT communication ... not permitted`

This means the installed APK attempted `ws://` while its release cleartext
policy is disabled, or it is an older APK with an incompatible network
configuration. Use a current debug build for local emulator/BlueStacks
development. Debug builds permit local HTTP only; release builds require a
real TLS endpoint:

```bash
./gradlew -Pmeizzo.releaseSignalingUrl=wss://signal.example \
  :apps:client:assembleRelease :apps:controller:assembleRelease
```

Do not disable certificate validation or enable unrestricted cleartext.

If `10.0.2.2` is not reachable in BlueStacks Air, it is a routing problem,
not an Android cleartext-policy problem. Use the Mac's reachable LAN address
and follow the [BlueStacks Air checklist](bluestacks-air.md).

## Offline status

Check that the backend responds to `GET /healthz`, that the app endpoint
matches the running service, and that Client and Controller are using the same
reachable host. The Controller connection indicator is separate from each
Client card. Backend timeout is 45 seconds; Client heartbeat interval is 15
seconds.

## Pairing fails

Use a fresh six-digit code, check that the Client approval prompt is visible,
and ensure the pairing offer has not expired. A code is single-use.

## Update refuses to install

The updater requires an app-specific asset, a matching entry in
`SHA256SUMS.txt`, the correct package ID, and the same signing certificate as
the installed app. A certificate mismatch is expected when an APK was signed
with a different key; do not bypass it.
