# Changelog

## v2.5.0

- Promoted the tested MEIZZO v2.5 foundation to the stable channel.
- Improved Client presence handling: a WebSocket close no longer immediately marks a device offline; the existing heartbeat timeout remains authoritative.
- Added foreground connection service support, Client session improvements, Controller alert management, and Cloudflare signaling updates.
- Preserved separate Android Client and Controller applications.

## Earlier releases

Historical beta releases remain available in GitHub Releases. Their original tags and assets are preserved.

## Versioning

MEIZZO uses `vX.Y.Z` for stable releases and `vX.Y.Z-beta.N` for beta releases. For example, `v2.5.0` is stable and the next preview line can use `v2.6.0-beta.1`. Android `versionCode` must increase for installable updates; `versionName` matches the release tag. Releases must preserve the stable Android signing key and must not publish unsigned or incorrectly signed APKs.
