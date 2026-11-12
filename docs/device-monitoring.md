# Device monitoring

The Android Client sends telemetry through authenticated heartbeats. The
current Android heartbeat cadence is 15 seconds. The hosted signaling Worker
uses a 45-second heartbeat timeout to mark a Client offline when fresh
heartbeats stop arriving.

Telemetry is a snapshot, not a continuous diagnostic stream. The Client currently sends battery health/temperature, RAM totals, and optional last-known location in addition to the basic fields. Not every field is surfaced in the Controller UI yet. Values depend on OS APIs, permissions, hardware, and app implementation.

| Field | Android beta | Notes |
|---|---|---|
| Battery percentage | Implemented | Read from Android battery APIs |
| Charging state | Implemented | Reports charging/not charging when available |
| Storage | Implemented | App/device filesystem totals as exposed by the implementation |
| Uptime | Implemented | Device uptime snapshot |
| Network transport | Implemented | Active network transport category |
| Manufacturer/model | Implemented | Android build metadata |
| App/OS version | Implemented | Android version and Client version are included in the heartbeat snapshot |
| RAM total/available | Implemented in Client snapshot | Controller presentation and vendor comparability still need validation |
| Battery health/temperature | Implemented in Client snapshot | Android battery APIs may return unknown/unsupported values; Controller presentation needs validation |
| Wi-Fi SSID/signal/IP | Not implemented as a general telemetry contract | Permission and OS restrictions apply |
| Latency | Not implemented | Do not display a fabricated zero |
| Thermal state | Not implemented as a verified field | Do not infer from device temperature |
| Last-known location | Opt-in prototype | Client exposes an explicit Find My location toggle and requests coarse/fine permission; sends cached coordinates only when available, not continuous GPS tracking. Controller display, staleness, and privacy UX need further validation |

## Presence semantics

- **Online:** authenticated Client connection with a heartbeat inside the
  server timeout window.
- **Offline:** connection closed or no heartbeat received before timeout.
- **Stale telemetry:** last snapshot is older than the UI freshness threshold.
- **Unavailable:** the OS, permission, hardware, or current implementation
  did not provide the field.

Unavailable is not the same as zero. The Controller should show the last
update time and avoid presenting stale values as live. The location toggle
must remain explicitly opt-in, and cached coordinates must be labeled with
their capture time and accuracy rather than represented as live tracking.

## Privacy

Telemetry is delivered to Controllers authorized for that Client. Do not
include pairing codes, authentication tokens, private content, or unnecessary
personal data in telemetry or logs. See
[security and privacy](security-and-privacy.md).
