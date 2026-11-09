import SwiftUI

@main
struct MeizzoClientApp: App {
    var body: some Scene { WindowGroup { ClientRootView() } }
}

struct ClientRootView: View {
    @StateObject private var model = ClientModel()
    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Image(systemName: "iphone.gen3")
                    .font(.system(size: 54))
                    .foregroundStyle(.tint)
                Text("MEIZZO Client").font(.largeTitle.bold())
                Text("Keep this screen open while pairing. Approve only people you trust.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
                Text(model.code).font(.system(size: 48, weight: .semibold, design: .monospaced))
                    .padding(.horizontal, 28).padding(.vertical, 18)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 22))
                Label(model.status, systemImage: model.connected ? "checkmark.circle.fill" : "wifi.slash")
                    .foregroundStyle(model.connected ? .green : .orange)
                TextField("wss://your-server.example", text: $model.endpoint)
                    .textInputAutocapitalization(.never).keyboardType(.URL)
                    .textFieldStyle(.roundedBorder)
                Button(model.connected ? "Reconnect" : "Connect") { model.connect() }
                    .buttonStyle(.borderedProminent)
                if let request = model.pendingRequest {
                    VStack(spacing: 10) {
                        Text("Pairing request").font(.headline)
                        Text("Controller \(request)")
                        HStack {
                            Button("Decline") { model.decide(false) }.buttonStyle(.bordered)
                            Button("Approve") { model.decide(true) }.buttonStyle(.borderedProminent)
                        }
                    }.padding().background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
                }
                if let error = model.error { Text(error).font(.footnote).foregroundStyle(.red) }
                Spacer()
            }.padding()
            .navigationTitle("Client")
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
    private var requestId: String?

    init() {
        socket.onMessage = { [weak self] message in Task { @MainActor in self?.handle(message) } }
    }

    func connect() {
        error = nil
        socket.connect(endpoint: endpoint)
        status = "Connecting…"
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            guard let self else { return }
            self.socket.send([
                "type": "client.register_pairing_offer", "clientId": id, "pairingCode": code,
                "expiresAtMs": Int(Date().timeIntervalSince1970 * 1000) + 600000,
                "pairingAcceptanceEnabled": true,
                "metadata": ["platform": "iOS", "model": "iPhone"]
            ])
        }
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
        case "client.pairing_request":
            requestId = message["requestId"] as? String
            pendingRequest = message["controllerId"] as? String ?? "Unknown"
        case "client.pairing_completed": status = "Paired successfully"
        case "error": error = message["message"] as? String
        default: break
        }
        connected = socket.connected
    }
}
