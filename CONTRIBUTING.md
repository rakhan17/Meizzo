# Contributing to MEIZZO

MEIZZO is a beta cross-platform device-assistance project. Contributions must
preserve explicit consent, isolated Client sessions, and honest platform
capability reporting.

## Workflow

1. Fork the repository and create a focused branch such as
   `fix/android-endpoint` or `docs/pairing-guide`.
2. Keep Client and Controller changes separate when their behavior differs.
3. Use conventional commit subjects (`feat:`, `fix:`, `docs:`, `test:`,
   `ci:`, `refactor:`).
4. Open a pull request describing behavior, affected platforms, security
   impact, documentation changes, and validation evidence.

Run the relevant checks before opening a pull request:

```bash
cd services/signaling && npm ci && npm run check && npm test
cd ../..
./gradlew :apps:client:assembleDebug :apps:controller:assembleDebug
```

macOS contributors should also run the iOS Simulator build and
`ios/scripts/simulator-smoke.sh` when changing iOS code. Do not include
credentials, private keys, pairing tokens, or real user data in commits,
issues, logs, screenshots, or tests.

Documentation changes must update links and state clearly when a feature is
unavailable or only verified in a Simulator/build environment.
