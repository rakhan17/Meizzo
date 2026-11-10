# Installation

## Android

Download the Client and Controller APK separately from the verified GitHub
release. Android may require the user to confirm installation from the browser
or file manager that opened the APK. Never install an APK whose package ID or
signature is not expected.

The in-app updater uses Android Package Installer after HTTPS download,
checksum validation, package validation, and signing-certificate comparison.

## iOS Simulator

Download the matching Simulator ZIP, extract the `.app`, and install it with
Xcode/Simulator tooling on macOS. This artifact is not an iPhone package.

## iPhone/iPad

Use TestFlight, App Store, development distribution, or a signed Ad Hoc IPA
with matching provisioning. The repository does not publish an IPA until the
manual Apple credential workflow succeeds.
