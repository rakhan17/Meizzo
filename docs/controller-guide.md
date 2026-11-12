# MEIZZO Controller guide

The v3 Android beta candidate uses three bottom tabs:

- **Link:** enter the six-digit pairing code to add a Client.
- **Devices:** manage paired Clients and open available device/session actions.
- **Settings:** configure light/dark appearance and Auto-Alert preferences.

After a pairing request is approved successfully, the Controller automatically switches from Link to Devices. Each paired Client has its own token, status, last-seen timestamp, and telemetry record.

## Devices

The device list displays each Client's name, online/offline state, platform, and available actions. Device actions remain dependent on backend authorization, Client availability, permissions, hardware support, and the active approved session. An offline device cannot answer a live snapshot request.

## Settings and appearance

The appearance setting switches between light and dark themes. System status and navigation bar icon contrast follows the selected theme. Auto-Alert controls are presented with the shared Liquid Glass toggle and slider components.

## Liquid Glass UI

The Android apps share the Compose module `apps/glass-ui`, built on AndroidLiquidGlass / Backdrop 2.0.1 and its upstream sample components. App-owned warnings, confirmations, and text-entry prompts use the shared Liquid Glass dialog surface. Android-owned permission prompts, document pickers, and Package Installer confirmation remain native system UI.

## Pairing and authorization

Use Link, enter six digits, and wait for Client approval. Controller stores the returned token separately for that Client. It authenticates stored tokens after reconnecting, so multiple Clients can share one Controller socket without sharing identity.

## Device actions

Rename, snapshot, revoke, flashlight, alarm, screen, camera, interaction, clipboard, remote file, and transfer actions are available only where the backend and Client implement them. Each sensitive capability requires its own Client approval. Pairing alone does not grant screen capture, camera, file access, or remote input.

## Updates and troubleshooting

Use **Check for app updates** to verify a matching Controller APK. If the certificate does not match the installed package, installation is refused. For backend problems, inspect the endpoint configuration and follow [troubleshooting](troubleshooting.md).

## Build/release status

The current v3 candidate's Client and Controller debug APKs passed CI. They are not signed public-release assets. The signed beta must be published by the release workflow after keystore, package, version, checksum, and certificate verification.
