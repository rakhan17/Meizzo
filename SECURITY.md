# Security policy

MEIZZO is a beta project. The currently supported branch is `main`; releases
are prereleases and may not be suitable for sensitive production workloads.

## Reporting a vulnerability

Do not open a public issue for an exploitable vulnerability. Use GitHub's
private vulnerability reporting feature for this repository, or contact the
repository owner through a private GitHub channel with:

- affected commit, release, app, or service;
- reproducible steps or a minimal proof of concept;
- impact and prerequisites;
- suggested mitigation, if known.

Never include passwords, tokens, private keys, pairing codes, personal data, or
production logs. If private reporting is unavailable, state that limitation
and request a private contact method from the maintainer.

Maintainers will acknowledge reports when possible, validate the issue,
coordinate a fix, and credit reporters only with permission.

## Security boundaries

Pairing requires explicit Client approval. Android credentials use Keystore and
iOS credentials use Keychain. Production deployments must use TLS/WSS. The
repository's in-memory backend is not a complete production security
architecture and must be deployed with appropriate persistence, rate limits,
logging controls, and access management.
