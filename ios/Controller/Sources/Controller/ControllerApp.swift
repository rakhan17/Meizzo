import SwiftUI

@main
struct MeizzoControllerApp: App {
    var body: some Scene { WindowGroup { ControllerRootView() } }
}

struct ControllerRootView: View {
    @StateObject private var model = ControllerModel()
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        Image("MeizzoLogo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 58, height: 58)
                            .accessibilityLabel("MEIZZO logo")
                        Text("MEIZZO Controller").font(.title2.bold())
                    }
                }
                Section("Connection") {
                    TextField("wss://your-server.example", text: $model.endpoint)
                        .textInputAutocapitalization(.never).keyboardType(.URL)
                    Button(model.connected ? "Reconnect" : "Connect") { model.connect() }
                }
                Section("Pair a Client") {
                    TextField("Six-digit code", text: $model.code).keyboardType(.numberPad)
                    Button("Request pairing") { model.pair() }.disabled(model.code.count != 6 || !model.connected)
                    if let message = model.pairingMessage { Text(message).foregroundStyle(.secondary) }
                }
                Section("Paired devices") {
                    if model.devices.isEmpty { Text("No Clients paired yet.").foregroundStyle(.secondary) }
                    ForEach(model.devices) { device in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(device.displayName).font(.headline)
                                Spacer()
                                Circle().fill(device.status == "online" ? .green : .gray).frame(width: 9, height: 9)
                            }
                            Text(device.clientId).font(.caption).foregroundStyle(.secondary)
                            Text(device.status.capitalized)
                                .font(.caption).foregroundStyle(device.status == "online" ? .green : .secondary)
                        }
                    }
                }
            }.navigationTitle("MEIZZO Controller")
        }
    }
}

@MainActor
final class ControllerModel: ObservableObject {
    @Published var endpoint = "ws://127.0.0.1:8080"
    @Published var code = ""
    @Published var devices: [MeizzoDevice] = []
    @Published var pairingMessage: String?
    @Published var connected = false
    private let controllerId = randomIdentifier(prefix: "ios-controller")
    private let socket = SignalingClient()
    private let store = SecureStore(service: "com.meizzo.controller.ios")

    init() {
        socket.onMessage = { [weak self] message in Task { @MainActor in self?.handle(message) } }
    }

    func connect() {
        socket.connect(endpoint: endpoint)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            guard let self else { return }
            if let token = self.store.value(for: "controller-token") {
                self.socket.send(["type": "auth.connect", "role": "controller", "authToken": token])
            }
        }
    }

    func pair() {
        pairingMessage = "Waiting for Client approval…"
        socket.send(["type": "controller.request_pairing", "controllerId": controllerId, "pairingCode": code])
    }

    private func handle(_ message: [String: Any]) {
        connected = socket.connected
        guard let type = message["type"] as? String else { return }
        if type == "controller.pairing_result", let status = message["status"] as? String {
            pairingMessage = status == "paired" ? "Pairing complete." : "Pairing \(status)."
            if let token = message["authToken"] as? String { try? store.save(token, key: "controller-token") }
            if let id = message["clientId"] as? String {
                devices.append(MeizzoDevice(clientId: id, status: "online", lastSeen: nil, metadata: ["platform": "iOS"]))
            }
        } else if type == "controller.device_list", let raw = message["devices"] as? [[String: Any]] {
            devices = raw.compactMap { item in
                guard let id = item["clientId"] as? String else { return nil }
                return MeizzoDevice(clientId: id, status: item["status"] as? String ?? "offline",
                                    lastSeen: (item["lastSeen"] as? NSNumber)?.int64Value,
                                    metadata: item["metadata"] as? [String: String] ?? [:])
            }
        } else if type == "device.status", let id = message["clientId"] as? String,
                  let index = devices.firstIndex(where: { $0.clientId == id }) {
            devices[index].status = message["status"] as? String ?? devices[index].status
        }
    }
}
