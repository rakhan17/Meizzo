# Installation

## Android

Download the latest Client and Controller APKs separately from the
[beta.5 GitHub Release](https://github.com/rakhan17/Meizzo/releases/tag/v0.1.0-beta.5).

- Install **MEIZZO Client** on the device receiving assistance.
- Install **MEIZZO Controller** on the operator device.
- Android may ask you to allow installation from the browser or file manager
  used to open the APK.
- Review the app identity and requested permissions before continuing.

The in-app updater downloads the app-specific release asset over HTTPS,
validates its SHA-256, package identity, and signing certificate, then
delegates installation to Android Package Installer. Android still requires
user confirmation.

If an older installation was signed with a different certificate, Android
will reject an in-place update. Do not bypass the signature check.

## iOS

iOS is still under development. CI Simulator builds are for engineering
validation and are not public physical-device downloads.

A physical iPhone/iPad build requires Apple signing and matching provisioning.
Use TestFlight, App Store, development distribution, or a properly signed
Ad Hoc IPA when that workflow is ready. A Simulator ZIP must never be renamed
to `.ipa`.
