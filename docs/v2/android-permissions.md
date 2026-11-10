# Android V2 permission model

| Capability | Platform boundary | V2 status |
|---|---|---|
| Network signaling | `INTERNET`, WSS/HTTPS | Implemented for beta signaling |
| Battery/storage/device metadata | Public Android APIs | Partial telemetry |
| Screen viewing | MediaProjection + foreground service + user confirmation | Not implemented |
| Remote interaction | User-enabled AccessibilityService, protected UI excluded | Not implemented |
| Camera | Runtime camera permission and active-session indication | Not implemented |
| Location | Runtime location permission and consent state | Not implemented |
| Flashlight | Camera/torch hardware and camera availability | Partial command path |
| Alarm | Audio/device policy constraints | Partial command path |
| Files | Storage Access Framework / MediaStore | Not implemented |
| Managed Mode | Legitimate Device Owner/Profile Owner enrollment | Not implemented |

Unavailable or denied data must be represented as unknown/unavailable, never
as a fabricated zero. Device Owner status must not be treated as permission
to bypass MediaProjection or runtime consent.
