# Device monitoring

Telemetry is sent in authenticated Client heartbeats every 15 seconds on
Android. The backend stores the latest snapshot in memory and broadcasts it
only to authorized Controller connections. iOS currently reports only fields
exposed by its public APIs and implemented by the target.

| Field | Meaning and source | Android | iOS | Availability |
|---|---|---:|---:|---|
| Battery percentage | `BatteryManager` capacity | Yes | Limited | Current snapshot |
| Charging | Battery status receiver | Yes | Limited | Current snapshot |
| Battery health/temp | Battery intent fields | When exposed | No verified implementation | Permission/API dependent |
| Network type | `ConnectivityManager` transport | Yes | Not equivalent | Current snapshot |
| Wi-Fi SSID/signal/IP | OS network APIs | Not currently sent | Not currently sent | Permission/API dependent |
| Internet connectivity | Active network capability | Partial | Not currently sent | Platform dependent |
| Latency | No production measurement field yet | Not available | Not available | Do not interpret as zero |
| Storage | `StatFs` app filesystem totals | Yes | Not currently sent | Current snapshot |
| RAM | `ActivityManager.MemoryInfo` | Yes | Not currently sent | Current snapshot |
| Manufacturer/model | Android build metadata | Yes | Platform metadata varies | Registration + heartbeat |
| OS/app version | Build metadata | Yes | App metadata | Registration + heartbeat |
| Uptime | `SystemClock.elapsedRealtime` | Yes | Not currently sent | Current snapshot |
| Thermal state | No verified production field | Not available | Not available | Do not fabricate |

The Controller should show the last update time and treat old data as stale.
An unavailable field means the operating system, permission, hardware, or
current implementation did not provide it. It must not be rendered as zero.

Location, arbitrary file access, screen capture, and unrestricted remote
control are not implemented monitoring fields. Explicit platform permissions
and iOS privacy restrictions remain binding.
