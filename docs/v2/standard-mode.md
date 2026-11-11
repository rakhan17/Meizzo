# Standard Mode

Standard Mode is the current beta trust model:

1. Client opens and presents a temporary six-digit pairing code.
2. Controller submits the code.
3. Client explicitly approves or rejects the pairing request.
4. Signaling issues separate credentials for Client and Controller.
5. Controller can monitor the paired Client's presence and available telemetry.
6. A Controller requests a scoped session before supported interaction
   commands can be routed.
7. Client explicitly approves or rejects each session request.

Pairing and session approval are separate decisions. A paired Controller does
not gain silent access to sensitive device capabilities.

The current session lifecycle is authorization metadata only. Screen capture,
camera streaming, file transfer, location sharing, and general remote input
remain unavailable until their platform permissions, transports, and
capability-specific consent flows are implemented and tested.
