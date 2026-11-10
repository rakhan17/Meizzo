import SwiftUI
import UIKit

@main
struct MeizzoClientApp: App {
    var body: some Scene { WindowGroup { ClientRootView() } }
}

struct ClientRootView: View {
    @StateObject private var model = ClientModel()
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                Image("MeizzoLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 72, height: 72)
                    .accessibilityLabel("MEIZZO logo")
                Text("MEIZZO Client").font(.largeTitle.bold()).foregroundStyle(.white)
                Text("Private device assistance, with your approval at every step.")
                    .foregroundStyle(.gray)
                VStack(alignment: .leading, spacing: 12) {
                    Label(model.status, systemImage: model.connected ? "checkmark.circle.fill" : "wifi.slash")
                        .foregroundStyle(model.connected ? .green : .orange)
                    Text("Pairing code").font(.caption).foregroundStyle(.gray)
                    Text(model.code).font(.system(size: 46, weight: .semibold, design: .monospaced))
                        .foregroundStyle(.white)
                    Text("Share this code only with a trusted Controller. It expires automatically.")
                        .font(.footnote).foregroundStyle(.gray)
                    Text("iOS limitation: remote touch control, file access, camera streaming, and background monitoring are not available in this beta.")
                        .font(.footnote).foregroundStyle(.gray)
                }.padding(22).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 24))
                if let request = model.pendingRequest {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Controller approval required").font(.headline).foregroundStyle(.white)
                        Text("Controller \(request) is requesting access. Approve only if you recognize it.")
                            .font(.subheadline).foregroundStyle(.gray)
                        HStack {
                            Button("Decline") { model.decide(false) }.buttonStyle(.bordered)
                            Button("Approve") { model.decide(true) }.buttonStyle(.borderedProminent)
                        }
                    }.padding(20).background(Color.orange.opacity(0.14), in: RoundedRectangle(cornerRadius: 22))
                }
                TextField("Signaling endpoint", text: $model.endpoint)
                    .textInputAutocapitalization(.never).keyboardType(.URL)
                    .textFieldStyle(.roundedBorder)
                Button(model.connected ? "Reconnect" : "Connect") { model.connect() }
                    .buttonStyle(.borderedProminent)
                if let error = model.error { Text(error).font(.footnote).foregroundStyle(.red) }
                Spacer(minLength: 24)
                }.padding(20)
            }.background(Color.black.ignoresSafeArea())
            .navigationTitle("Client")
            .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }
}

@MainActor
final class ClientModel: ObservableObject {
    @Published var endpoint = "ws://127.0.0.1:8080"
    @Published var code = randomPairingCode()
    @Published var status = "Not connected"
    @Published var pendingRequest: String?
    @Published var error: String?
    @Published var connected = false
    private let id = randomIdentifier(prefix: "ios-client")
    private let socket = SignalingClient()
    private let store = SecureStore(service: "com.meizzo.client.ios")
    private var requestId: String?

    init() {
        socket.onMessage = { [weak self] message in Task { @MainActor in self?.handle(message) } }
        socket.onStateChange = { [weak self] state in
            Task { @MainActor in
                self?.connected = state == "Connected"
                if state != "Connected" { self?.status = state }
            }
        }
        socket.onConnected = { [weak self] in
            guard let self else { return }
            self.socket.send([
                "type": "client.register_pairing_offer", "clientId": self.id, "pairingCode": self.code,
                "expiresAtMs": Int(Date().timeIntervalSince1970 * 1000) + 600000,
                "pairingAcceptanceEnabled": true, "metadata": self.metadata()
            ])
            if let token = self.store.value(for: "auth-token") {
                self.socket.send(["type": "auth.connect", "role": "client", "clientId": self.id, "authToken": token])
            }
        }
        socket.heartbeatPayload = { [weak self] in self?.telemetry() ?? [:] }
    }

    func connect() {
        error = nil
        socket.connect(endpoint: endpoint)
        status = "Connecting…"
    }

    func decide(_ approved: Bool) {
        guard let requestId else { return }
        socket.send(["type": "client.pairing_decision", "requestId": requestId, "approved": approved])
        pendingRequest = nil
        self.requestId = nil
    }

    private func handle(_ message: [String: Any]) {
        switch message["type"] as? String {
        case "client.pairing_offer_registered": status = "Ready to pair"
        case "auth.connected": status = "Online · waiting for Controller"
        case "client.pairing_request":
            requestId = message["requestId"] as? String
            pendingRequest = message["controllerId"] as? String ?? "Unknown"
        case "client.pairing_completed":
            if let token = message["authToken"] as? String {
                try? store.save(token, key: "auth-token")
                socket.send(["type": "auth.connect", "role": "client", "clientId": id, "authToken": token])
            }
            status = "Paired successfully"
        case "error": error = message["message"] as? String
        default: break
        }
        connected = socket.connected
    }

    private func metadata() -> [String: String] {
        ["platform": "iOS", "model": UIDevice.current.model, "systemVersion": UIDevice.current.systemVersion,
         "clientVersion": "0.1.0-beta.3"]
    }

    private func telemetry() -> [String: Any] {
        UIDevice.current.isBatteryMonitoringEnabled = true
        var result: [String: Any] = [
            "charging": UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full,
            "timestamp": Int(Date().timeIntervalSince1970 * 1000)
        ]
        if UIDevice.current.batteryLevel >= 0 {
            result["batteryPercent"] = Int(UIDevice.current.batteryLevel * 100)
        }
        return result
    }
}
