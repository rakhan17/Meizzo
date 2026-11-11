# User guide

## Client and Controller

The **Client** is installed on the device receiving assistance. The
**Controller** is installed on the operator device. They are separate
applications with separate Android package IDs and iOS bundle IDs.

## Pairing

1. Open Client and note the displayed six-digit code.
2. Open Controller and choose **Add device**.
3. Enter the code exactly as shown.
4. Review the request on Client and choose **Approve** or **Reject**.
5. After approval, Controller stores its authorization token and the Client
   appears in the device list.

Codes are temporary and single-use. Pairing always requires explicit approval
on Client.

## Multiple Clients

Repeat the pairing flow for each Client. Each device has its own identity and
authorization relationship. Disconnecting Client A must not change Client B.
Select a device card to inspect its latest metadata and telemetry.

## Statuses

- **Controller connected:** Controller's signaling socket is authenticated.
- **Connecting/reconnecting:** the app is attempting to establish or restore
  its signaling connection.
- **Client online:** the backend has a recent authenticated Client heartbeat.
- **Client offline:** the connection closed or the heartbeat timeout elapsed.
- **Pending approval:** a pairing or session request is awaiting a Client
  decision.

## Sessions

Pairing and session approval are separate. Controller can request a scoped
session for a paired Client. Client must explicitly approve or reject it.
Controller can end its session.

An approved session is authorization state only. It does not create a screen
share, camera stream, file transfer, location feed, or unrestricted input
channel. Supported interaction commands require an approved interaction
session and remain limited to implemented actions.

## Monitoring

Battery, charging, storage, network transport, uptime, and device metadata are
reported when the platform exposes them. Unsupported or permission-protected
fields are unavailable, not zero. See [device monitoring](device-monitoring.md).

## Disconnect, revoke, and reconnect

Closing the Client connection causes the backend to broadcast offline status.
Reopening the app or restoring its network starts reconnect backoff. Android
foreground services improve continuity, but force-stop, battery optimization,
OEM restrictions, device shutdown, or network loss can interrupt the service.

A Controller can revoke its relationship with a device. Pairing must then be
performed again. Hosted signaling state is stored by the Durable Object;
the local Node.js development harness is in-memory and loses state on restart.

## Updates

Android **Check for app updates** queries GitHub Releases, selects the
app-specific APK, verifies release checksum and package identity, compares the
APK signing certificate, then delegates installation to Android Package
Installer. It never silently installs or downgrades an app.

iOS uses Apple-supported distribution methods. Simulator ZIP files are not
installable iOS packages.

## Bug reports

Use the [bug template](https://github.com/rakhan17/Meizzo/blob/main/.github/ISSUE_TEMPLATE/bug_report.yml).
Include app version, platform, OS, device model, steps, expected and actual
behavior, and sanitized logs. Never include tokens, passwords, pairing codes,
private URLs, or personal data.
