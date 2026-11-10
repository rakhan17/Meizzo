# Contributing to MEIZZO

Thank you for considering a contribution. MEIZZO is a beta, cross-platform device-assistance project. Changes must preserve explicit Client consent, authenticated sessions, device isolation, and honest reporting of platform capabilities.

Before contributing, read the [Code of Conduct](CODE_OF_CONDUCT.md), [Security Policy](SECURITY.md), [architecture guide](docs/architecture.md), and [implementation status](docs/implementation-status.md).

## 1. Ways to contribute

- Report reproducible defects with sanitized logs.
- Improve onboarding, accessibility, translations, and documentation.
- Add tests for pairing, authentication, presence, telemetry, or reconnect behavior.
- Improve Android and iOS platform-specific implementations.
- Review security boundaries and dependency updates.
- Improve build automation and release reproducibility.

For substantial changes, open an issue first to discuss scope and compatibility.

## 2. Development principles

### Consent and authorization

- A pairing code is temporary discovery, not authorization.
- The Client must explicitly approve every new pairing request.
- Never bypass permission prompts or hide active session indicators.
- Revocation must invalidate the relevant authorization.
- A Controller must never access a Client outside its authenticated relationship.

### Platform honesty

- Do not claim iOS capabilities that public iOS APIs do not provide.
- Distinguish build success, automated tests, Simulator smoke tests, and physical-device runtime tests.
- Unsupported telemetry must be unavailable, not fabricated as zero.
- Do not mark a feature complete because a button or placeholder screen exists.

### Privacy and secure development

- Never commit tokens, pairing codes, signing keys, provisioning profiles, private endpoints, or real user data.
- Keep secrets out of screenshots, logs, fixtures, and issue descriptions.
- Use TLS/WSS for production signaling.
- Do not disable certificate validation or broaden cleartext exceptions to hide connectivity failures.

## 3. Repository layout

| Path | Responsibility |
|---|---|
| `apps/client` | Android Client |
| `apps/controller` | Android Controller |
| `ios/Client`, `ios/Controller` | Native iOS app targets |
| `ios/Shared` | Shared iOS networking and secure storage |
| `services/signaling` | WebSocket signaling backend |
| `docs` | User, developer, platform, and operations guides |
| `.github/workflows` | CI, Simulator, device-signing, release, and docs workflows |

## 4. Local prerequisites

- JDK 17.
- Android SDK platform 35 and build tools 35.0.0.
- Node.js 22 and npm.
- Xcode and the project's XcodeGen setup for iOS work.
- Git and a GitHub account.

Use the platform guides for exact setup: [Android](docs/android-guide.md), [iOS](docs/ios-guide.md), and [development](docs/development.md).

## 5. Run checks

### Signaling backend

```bash
cd services/signaling
npm ci
npm run check
npm test
```

### Android

```bash
./gradlew :apps:client:assembleDebug
./gradlew :apps:controller:assembleDebug
```

For BlueStacks Air, supply the Mac's reachable LAN endpoint explicitly:

```bash
./gradlew \
  -Pmeizzo.debugSignalingUrl=ws://192.168.1.20:8080 \
  :apps:client:assembleDebug :apps:controller:assembleDebug
```

Replace the sample address with the actual host address. See [BlueStacks Air setup](docs/bluestacks-air.md).

### iOS

Run the iOS Simulator workflow and the repository's simulator smoke script on macOS when changing iOS targets. A successful Simulator launch is not proof of physical-device signing or full end-to-end pairing.

### Documentation

Install dependencies from `requirements-docs.txt`, then run the strict MkDocs build used by CI. Resolve broken internal links and warnings before submitting.

## 6. Branches and commits

Use focused branches, for example:

- `fix/android-network-policy`
- `feat/controller-device-details`
- `test/presence-reconnect`
- `docs/bluestacks-air-guide`

Use concise conventional commit prefixes: `feat:`, `fix:`, `docs:`, `test:`, `ci:`, `refactor:`, or `build:`.

Avoid unrelated formatting or generated-file churn in functional changes.

## 7. Pull request requirements

A useful pull request includes:

1. **Problem:** what user or developer problem is addressed?
2. **Scope:** which apps, service, workflows, or docs change?
3. **Behavior:** what changes for Client and Controller?
4. **Security:** does the change affect consent, tokens, permissions, transport, or session isolation?
5. **Compatibility:** are package IDs, bundle IDs, stored credentials, and update signatures preserved?
6. **Validation:** list exact commands and workflow links with results.
7. **Limitations:** identify tests not run and capabilities not supported.
8. **Visual evidence:** include before/after screenshots for UI changes, with private data removed.

### Pull request checklist

- [ ] The change has a focused scope and clear rationale.
- [ ] Client and Controller responsibilities remain distinct.
- [ ] Consent, authentication, revocation, and session isolation are preserved.
- [ ] Relevant tests were added or updated.
- [ ] Android and iOS limitations are accurately documented.
- [ ] Documentation and navigation links are updated.
- [ ] No secrets, personal data, or generated build outputs were committed.
- [ ] Build and test evidence is reported without exaggeration.

## 8. Review and merge

Maintainers may request changes when a PR lacks reproducible evidence, weakens security boundaries, introduces unverified platform claims, or leaves documentation inconsistent. Keep review discussion respectful and technical.

## 9. License and contributions

No root-level project license has been finalized. Do not assume that public repository visibility grants reuse or redistribution rights. The repository owner must select and add a license before the project can claim a specific open-source license. See [license status](docs/license.md).

By submitting a contribution, you confirm that you have the right to submit it and that you are not knowingly including third-party material without the necessary rights. This statement does not replace a future formal contributor license agreement.
