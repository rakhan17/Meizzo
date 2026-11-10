# MEIZZO Security Policy

MEIZZO is a beta device-assistance project. Security reports are welcome, but please disclose vulnerabilities responsibly so users are not exposed while a fix is prepared.

## Supported versions

| Version | Security support |
|---|---|
| Latest published beta | Best-effort review; prerelease software may contain unresolved defects |
| Older beta releases | Upgrade to the latest supported build where possible |
| Unreleased commits | Development only; no release-level support guarantee |

The `main` branch is the active development line. This table does not imply that any beta is suitable for sensitive production use.

## Report a vulnerability privately

**Do not publish an exploitable vulnerability in a public issue, discussion, or pull request.**

Use GitHub's private vulnerability reporting feature for this repository if it is enabled. Otherwise contact the repository owner through a private GitHub channel and request a secure reporting route.

Include, where safe:

- Affected app, service, release, commit, and platform.
- Impact and realistic attack prerequisites.
- Reproduction steps or a minimal proof of concept.
- Relevant sanitized logs and timestamps.
- Suggested mitigation, if known.

Do not include passwords, access tokens, pairing codes, private keys, provisioning profiles, personal data, or production customer information.

## What to expect

The maintainer will make a reasonable effort to:

1. Acknowledge the report.
2. Reproduce and assess severity.
3. Coordinate a fix and affected-version assessment.
4. Prepare a release or mitigation where appropriate.
5. Credit the reporter if they consent.

MEIZZO is maintained on a best-effort basis; no fixed response-time SLA is promised.

## Security boundaries to preserve

### Pairing and identity

- Pairing codes must be short-lived, rate-limited, and single-use.
- A pairing code must never replace explicit Client approval.
- Tokens must be scoped to the correct Client and Controller relationship.
- Revocation must prevent subsequent use of the revoked authorization.

### Transport and credentials

- Production signaling must use HTTPS/WSS with normal certificate validation.
- Cleartext exceptions must be limited to explicitly configured debug builds.
- Android credentials belong in Android Keystore-backed storage; iOS credentials belong in Keychain.
- Secrets must never be written to logs or committed to the repository.

### Presence, telemetry, and commands

- Presence and telemetry must be associated with authenticated identities.
- A stale disconnect must not overwrite a newer active connection.
- Events and commands must not cross Client session boundaries.
- Sensitive capabilities must require the platform permission and explicit user consent.

## Known architectural limitations

The current signaling service uses in-memory state. Restarting the service can invalidate active pairing/session state. A production deployment needs a reviewed persistence strategy, TLS termination, operational monitoring, rate limiting, access control, backup/recovery planning, and a documented incident process.

iOS and Android expose different capabilities. Do not treat an unavailable API or permission as a security bypass opportunity.

## Dependency and secret hygiene

Report suspected dependency compromise, leaked signing material, or exposed credentials privately. If a repository secret is exposed, rotate or revoke it immediately; deleting it from the latest commit is not sufficient because it may remain in Git history or logs.
