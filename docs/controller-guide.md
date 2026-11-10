# MEIZZO Controller guide

Controller maintains an independent backend connection indicator and a device
list. Each paired Client has its own token, status, last-seen timestamp, and
telemetry record.

## Dashboard

The summary counts paired, online, and offline devices. A device card shows
the alias or model, platform, presence, battery when available, network type,
and app-generated identifier. **Open device details** displays the latest
snapshot; **Refresh telemetry** requests a live snapshot from an online Client.

## Pairing and authorization

Use **Add device**, enter six digits, and wait for Client approval. Controller
stores the returned token separately for that Client. It authenticates every
stored token after reconnecting, so multiple Clients can share one Controller
socket without sharing identity.

## Device actions

Rename, snapshot, revoke, flashlight, and alarm are only available where the
backend and Client implement them. An offline device cannot answer a snapshot
request. No UI status is a substitute for backend presence.

## Updates and troubleshooting

Use **Check for app updates** to verify a matching Controller APK. If the
certificate does not match the installed package, installation is refused.
For backend problems, inspect the endpoint configuration and follow
[troubleshooting](troubleshooting.md).
