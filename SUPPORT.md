# MEIZZO Support

This page helps route questions and bug reports to the right place. MEIZZO is beta software; some capabilities and distribution paths are still under development.

## Start here

| If you need help with… | Read |
|---|---|
| First installation and pairing | [Getting started](docs/getting-started.md) |
| Client approval and Controller workflow | [User guide](docs/user-guide.md) |
| BlueStacks Air networking | [BlueStacks Air guide](docs/bluestacks-air.md) |
| Battery, network, and device information | [Device monitoring](docs/device-monitoring.md) |
| Android installation or updates | [Android guide](docs/android-guide.md) |
| Simulator ZIP vs physical iPhone IPA | [iOS guide](docs/ios-guide.md) |
| Common errors | [Troubleshooting](docs/troubleshooting.md) |
| Known limitations | [Implementation status](docs/implementation-status.md) |

## Where to ask

- **Bug:** use the repository's Bug Report issue template.
- **Feature request:** use the Feature Request issue template.
- **Usage question:** use GitHub Discussions if enabled, otherwise open a question issue.
- **Security vulnerability:** follow [SECURITY.md](SECURITY.md), not a public issue.
- **Conduct concern:** contact maintainers privately as described in [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Include useful diagnostic information

A reproducible report should include:

- Application: Client or Controller.
- App version and source: published Release, Actions artifact, or locally built APK.
- Package/bundle identifier.
- Platform, OS version, and device/emulator model.
- Backend endpoint type (local HTTP, LAN HTTP for debug, or production WSS). Do not reveal private hostnames or credentials.
- Exact steps, expected result, and actual result.
- Approximate timestamp and sanitized error/log excerpt.
- Whether the issue reproduces on another instance or network.

For BlueStacks Air, include whether the backend runs on the Mac, which LAN interface is used, whether the backend health endpoint responds from the host, and whether the emulator can reach the configured host/port.

## Protect your data

Never attach pairing codes, tokens, passwords, private keys, signing files, Wi-Fi passwords, personal files, or unredacted device identifiers. Redact IP addresses if they identify a private environment you do not want to disclose.

## Response expectations

MEIZZO is maintained on a best-effort basis. Please do not assume a guaranteed response time or support SLA. Check the latest release notes and existing issues before opening a duplicate report.
