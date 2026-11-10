# User guide

## Client and Controller

The **Client** is installed on the device receiving assistance. The **Controller**
is installed on the operator device. They are separate applications with
separate Android package IDs and iOS bundle IDs.

## Pairing

1. Open Client and copy the displayed six-digit code.
2. Open Controller and choose **Add device**.
3. Enter the code exactly as shown.
4. Review the request on Client and choose **Approve** or **Reject**.
5. After approval, Controller stores its authorization token and the device
   appears in the dashboard.

Codes are temporary, single-use, and require explicit Client approval. See
[pairing](pairing.md) for protocol details.

## Multiple Clients

Repeat the flow for each Client. Each card is keyed by its own app-generated
device identifier. Disconnecting Client A must not change Client B. Select a
card to inspect its latest metadata and telemetry.

## Statuses

- **Controller connected:** the Controller WebSocket is authenticated with the
  backend.
- **Connecting/reconnecting:** the Controller is attempting to establish or
  restore its backend connection.
- **Client online:** the backend has an authenticated Client connection with a
  heartbeat inside the timeout window.
- **Client offline:** the connection closed or no heartbeat was received before
  the backend timeout.
- **Pending approval:** a pairing request is awaiting an explicit Client choice.

## Monitoring

Battery, charging, storage, network, RAM, uptime, and device metadata are
reported when the platform exposes them. Unsupported or permission-protected
fields are unavailable, not zero. See [device monitoring](device-monitoring.md).

## Disconnect, revoke, and reconnect

Closing the Client connection causes the backend to broadcast offline status.
Reopening the app or restoring its network starts the reconnect backoff. A
Controller can remove a device using the implemented revoke flow; pairing must
then be performed again. Restarting the current in-memory backend invalidates
active sessions.

## Updates

Android **Check for app updates** queries the public GitHub Releases API,
matches the app-specific APK, verifies the release checksum and package ID,
compares the APK signing certificate, then delegates installation to Android's
package installer. It never silently installs or downgrades an app. iOS apps
must use TestFlight, the App Store, or an appropriately signed device build;
Simulator ZIP files are not installable iOS packages.

## Bug reports

Use the [bug template](https://github.com/rakhan17/Meizzo/blob/main/.github/ISSUE_TEMPLATE/bug_report.yml). Include app,
version, platform, OS, device model, steps, expected and actual behavior, and
sanitized logs. Never include tokens, passwords, pairing codes, private URLs,
or personal data.
