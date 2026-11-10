# FAQ

### Can I install the iOS Simulator ZIP on an iPhone?

No. It contains an unsigned Simulator `.app`. A physical iPhone/iPad requires
a signed device IPA, development/Ad Hoc provisioning, TestFlight, or App
Store distribution.

### Does MEIZZO provide silent remote control?

No. Pairing requires explicit Client approval, and platform permissions cannot
be bypassed. Android command support is limited to implemented actions.

### Why is a device offline while the Controller is connected?

These are separate states. Controller connectivity only proves the Controller
socket is authenticated. Client presence requires the backend to receive a
recent authenticated heartbeat.

### Does restarting the backend preserve pairings?

No. The current backend is in-memory and restart invalidates active state.

### Can I publish an Android update signed by any key?

No. Android requires the update certificate to match the installed app.
MEIZZO release CI now requires a stable protected keystore secret.
