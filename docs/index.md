# MEIZZO documentation

MEIZZO is a consent-first device assistance platform with separate Android
Client and Controller applications, a protocol-v2 signaling control plane,
device presence and telemetry, and an authorization-only session lifecycle.
The current public Android channel is beta. iOS remains under development.

This documentation distinguishes implemented behavior from planned
capabilities. A successful build or Simulator launch is not evidence of
physical-device or full end-to-end functionality.

## Choose your path

| Audience | Start here |
|---|---|
| New Android user | [Getting started](getting-started.md) |
| Client user | [Client guide](client-guide.md) |
| Controller user | [Controller guide](controller-guide.md) |
| Pairing and authorization | [Pairing guide](pairing.md) |
| Device telemetry | [Monitoring reference](device-monitoring.md) |
| Android developer | [Android guide](android-guide.md) |
| iOS developer | [iOS guide](ios-guide.md) |
| Backend operator | [Backend deployment](backend-deployment.md) |
| Protocol developer | [API reference](api-reference.md) |
| Contributor | [Development](development.md) |
| Security reporter | [Security and privacy](security-and-privacy.md) |

## System references

- [Current architecture](architecture.md)
- [Protocol/API reference](api-reference.md)
- [Implementation status and limitations](implementation-status.md)
- [Security and privacy](security-and-privacy.md)
- [Testing and acceptance matrix](testing.md)
- [Release notes](release-notes.md)
- [Detailed update log](updates.md)
- [V2 architecture audit](v2/architecture-audit.md)
- [V2 security model](v2/security-model.md)

## Build the documentation site

```bash
python -m pip install -r requirements-docs.txt
python scripts/check-doc-links.py
mkdocs build --strict
```

The MkDocs Material configuration is stored in the repository-root
`mkdocs.yml`. Documentation CI checks internal links and builds the site in
strict mode.
