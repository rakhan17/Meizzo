# Device monitoring

The Android Client sends telemetry through authenticated heartbeats. The
current Android heartbeat cadence is 15 seconds. The hosted signaling Worker
uses a 45-second heartbeat timeout to mark a Client offline when fresh
heartbeats stop arriving.

Telemetry is a snapshot, not a continuous diagnostic stream. Values depend
on OS APIs, permissions, hardware, and app implementation.

| Field | Android beta | Notes |
|---|---|---|
| Battery percentage | Implemented | Read from Android battery APIs |
| Charging state | Implemented | Reports charging/not charging when available |
| Storage | Implemented | App/device filesystem totals as exposed by the implementation |
| Uptime | Implemented | Device uptime snapshot |
| Network transport | Implemented | Active network transport category |
| Manufacturer/model | Implemented | Android build metadata |
| App/OS version | Partial | Depends on registration and platform metadata |
| RAM | Not guaranteed | Show only if the current app snapshot provides it |
| Battery health/temperature | Not guaranteed | Not a stable cross-device telemetry contract |
| Wi-Fi SSID/signal/IP | Not implemented as a general telemetry contract | Permission and OS restrictions apply |
| Latency | Not implemented | Do not display a fabricated zero |
| Thermal state | Not implemented as a verified field | Do not infer from device temperature |
| Location | Not implemented | Requires a separate consent and permission design |

## Presence semantics

- **Online:** authenticated Client connection with a heartbeat inside the
  server timeout window.
- **Offline:** connection closed or no heartbeat received before timeout.
- **Stale telemetry:** last snapshot is older than the UI freshness threshold.
- **Unavailable:** the OS, permission, hardware, or current implementation
  did not provide the field.

Unavailable is not the same as zero. The Controller should show the last
update time and avoid presenting stale values as live.

## Privacy

Telemetry is delivered to Controllers authorized for that Client. Do not
include pairing codes, authentication tokens, private content, or unnecessary
personal data in telemetry or logs. See
[security and privacy](security-and-privacy.md).
