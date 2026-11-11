# FAQ

### What is the latest Android version?

The latest public Android prerelease is
[`v0.1.0-beta.5`](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.5).
Client and Controller are separate APKs.

### Can I install the iOS Simulator ZIP on an iPhone?

No. A Simulator `.app` or ZIP is not an iPhone package. A physical iPhone
requires signed device distribution and matching provisioning, such as
TestFlight, App Store, development, or Ad Hoc distribution. iOS is still under
development.

### Does MEIZZO provide silent remote control?

No. Pairing requires explicit Client approval, and each session request
requires a separate Client decision. Session approval is authorization state,
not a screen-sharing or unrestricted remote-control channel. Android command
support is limited to implemented actions.

### Why is a device offline while the Controller is connected?

These are separate states. Controller connectivity proves only that the
Controller socket is authenticated. Each Client's presence requires its own
authenticated connection and recent heartbeat.

### Does restarting the backend preserve pairings?

The hosted Cloudflare Worker uses Durable Object storage for signaling state.
The local Node.js development harness uses in-memory state and loses offers,
relationships, and sessions when its process restarts. Do not assume the
development harness has the hosted persistence behavior.

### Why does a background connection stop?

Android and device manufacturers may restrict background execution through
battery optimization, autostart settings, force-stop, or other power policies.
Foreground services improve continuity but cannot override these restrictions.

### Can I publish an Android update signed by any key?

No. Android requires the update certificate to match the installed app.
MEIZZO's updater validates the checksum, package identity, and signing
certificate before offering installation.
