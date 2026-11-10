# V2 testing strategy

## Evidence classes

| Class | Evidence currently available |
|---|---|
| Unit/static | Node syntax check; Android compilation in CI |
| Backend integration | Pairing, approval, token auth, heartbeat, telemetry, reconnect, multi-device isolation |
| Emulator/BlueStacks | Manual only; no repository runner has access to the user's BlueStacks Air |
| Physical Android | Not tested |
| MediaProjection/Accessibility | Not implemented and therefore not tested |
| iOS | Simulator build and smoke launch only |
| Release installation | Updater verification code exists; physical install is not tested |

Every new capability must add a contract test before UI claims it is
available. Runtime test reports must state whether they used CI, an emulator,
BlueStacks, or physical hardware.
