<div class="meizzo-kicker">MEIZZO / DOCUMENTATION</div>

# One ecosystem. Two roles. Clear consent.

<p class="meizzo-lead">The field guide for installing, operating, testing, and responsibly extending MEIZZO across Android and iOS.</p>

<div class="grid cards" markdown>

-   :material-rocket-launch-outline: **Get started**

    ---

    Install the right app, configure a reachable signaling endpoint, and complete a consent-based pairing.

    [Quick start :material-arrow-right:](getting-started.md)

-   :material-cellphone-link: **Operate MEIZZO**

    ---

    Understand Client approval, Controller device management, connection states, and device telemetry.

    [User manual :material-arrow-right:](user-guide.md)

-   :material-code-braces: **Build and test**

    ---

    Configure toolchains, understand the signaling service, run regression checks, and contribute changes.

    [Developer setup :material-arrow-right:](development.md)

-   :material-shield-lock-outline: **Security and privacy**

    ---

    Review pairing authorization, secure storage, platform boundaries, and responsible disclosure.

    [Security guide :material-arrow-right:](security-and-privacy.md)

</div>

## Choose your path

=== "I'm installing MEIZZO"

    1. Read [Getting started](getting-started.md).
    2. Install the separate [Client](client-guide.md) and [Controller](controller-guide.md).
    3. Follow [Pairing](pairing.md).
    4. Review [Device monitoring](device-monitoring.md).
    5. Keep [Troubleshooting](troubleshooting.md) nearby.

=== "I'm developing MEIZZO"

    1. Review [Architecture](architecture.md).
    2. Configure [Development](development.md).
    3. Read the [API reference](api-reference.md).
    4. Run the [test plan](testing.md).
    5. Review [Backend deployment](backend-deployment.md).

=== "I'm evaluating the project"

    - [Implementation status](implementation-status.md) separates verified work from planned capabilities.
    - [Release notes](release-notes.md) explains artifact types and versioning.
    - [License status](license.md) records the owner decision still required.
    - [Contributing](../CONTRIBUTING.md) explains how to propose changes.

## Platform guides

<div class="grid cards" markdown>

-   **Android Client and Controller**

    ---

    Separate APKs, debug endpoint configuration, Android permissions, update flow, and BlueStacks Air notes.

    [Android guide](android-guide.md)

-   **iOS Client and Controller**

    ---

    Native SwiftUI targets, Simulator artifacts, device signing, provisioning, and TestFlight constraints.

    [iOS guide](ios-guide.md)

-   **Signaling service**

    ---

    Pairing, token authentication, heartbeat, presence, telemetry, and session routing.

    [Backend deployment](backend-deployment.md)

</div>

## Evidence matters

!!! warning "A successful build is not an end-to-end test"
    Android CI proves compilation and packaging. iOS Simulator smoke tests prove installation and launch in Simulator. Neither alone proves physical-device pairing or cross-network connectivity.

See [implementation status](implementation-status.md) and [testing](testing.md) for what has and has not been verified.

## Documentation index

| Area | Guides |
|---|---|
| User | [Quick start](getting-started.md) · [User manual](user-guide.md) · [FAQ](faq.md) |
| Apps | [Client](client-guide.md) · [Controller](controller-guide.md) · [Pairing](pairing.md) |
| Monitoring | [Device monitoring](device-monitoring.md) · [Troubleshooting](troubleshooting.md) |
| Platforms | [Android](android-guide.md) · [iOS](ios-guide.md) · [BlueStacks Air](bluestacks-air.md) |
| Engineering | [Architecture](architecture.md) · [API](api-reference.md) · [Development](development.md) |
| Operations | [Testing](testing.md) · [Backend deployment](backend-deployment.md) |
| Project | [Roadmap](roadmap.md) · [Release notes](release-notes.md) · [License](license.md) |
| Community | [Contributing](../CONTRIBUTING.md) · [Security](../SECURITY.md) · [Support](../SUPPORT.md) · [Code of Conduct](../CODE_OF_CONDUCT.md) |
