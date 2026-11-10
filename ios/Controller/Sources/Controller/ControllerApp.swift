import SwiftUI

@main
struct MeizzoControllerApp: App {
    var body: some Scene { WindowGroup { ControllerRootView() } }
}

struct ControllerRootView: View {
    @StateObject private var model = ControllerModel()
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        Image("MeizzoLogo").resizable().scaledToFit().frame(width: 56, height: 56)
                        VStack(alignment: .leading) {
                            Text("MEIZZO").font(.title.bold())
                            Text("Controller").foregroundStyle(.gray)
                        }
                    }
                    HStack(spacing: 12) {
                        metricCard("\(model.devices.count)", "Paired")
                        metricCard("\(model.devices.filter { $0.status == "online" }.count)", "Online")
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Connection").font(.headline)
                    Label(model.connectionState, systemImage: model.connected ? "checkmark.circle.fill" : "wifi.slash")
                        .foregroundStyle(model.connected ? .green : .orange)
                        TextField("Signaling endpoint", text: $model.endpoint)
                        .textInputAutocapitalization(.never).keyboardType(.URL)
                        Button(model.connected ? "Reconnect" : "Connect") { model.connect() }.buttonStyle(.borderedProminent)
                    }.padding(18).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Pair a Client").font(.headline)
                        Text("Enter the six-digit code shown on the Client.")
                            .font(.subheadline).foregroundStyle(.gray)
                        TextField("123456", text: $model.code).keyboardType(.numberPad)
                            .textFieldStyle(.roundedBorder)
                        Button("Request pairing") { model.pair() }
                            .buttonStyle(.borderedProminent).disabled(model.code.count != 6 || !model.connected)
                        if let message = model.pairingMessage { Text(message).font(.footnote).foregroundStyle(.gray) }
                    }.padding(18).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
                    Text("Your devices").font(.title2.bold()).foregroundStyle(.white)
                    if model.devices.isEmpty {
                        Text("No Clients paired yet.").foregroundStyle(.gray)
                    } else {
                        ForEach(model.devices) { device in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(device.displayName).font(.headline).foregroundStyle(.white)
                                    Spacer()
                                    Circle().fill(device.status == "online" ? .green : .gray).frame(width: 9, height: 9)
                                }
                                Text("\(device.status.capitalized) · \(device.metadata["platform"] ?? "Platform unavailable")")
                                    .font(.subheadline).foregroundStyle(device.status == "online" ? .green : .gray)
                                Text(device.clientId).font(.caption).foregroundStyle(.gray)
                                if let telemetry = model.telemetryByDevice[device.clientId] {
                                    Text("Battery \(telemetry["batteryPercent"] ?? "unavailable")% · \(telemetry["network"] ?? "network unavailable")")
                                        .font(.caption).foregroundStyle(.gray)
                                }
                            }.padding(18).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
                        }
                    }
                }.padding(20)
            }.background(Color.black.ignoresSafeArea())
                .navigationTitle("Dashboard")
                .toolbarColorScheme(.dark, for: .navigationBar)
        }
    }

    private func metricCard(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading) {
            Text(value).font(.title.bold()).foregroundStyle(.white)
            Text(label).font(.caption).foregroundStyle(.gray)
        }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 18))
    }
}

@MainActor
final class ControllerModel: ObservableObject {
    @Published var endpoint = "ws://127.0.0.1:8080"
    @Published var code = ""
    @Published var devices: [MeizzoDevice] = []
    @Published var pairingMessage: String?
    @Published var connected = false
    @Published var connectionState = "Disconnected"
    @Published var telemetryByDevice: [String: [String: String]] = [:]
    private let controllerId = randomIdentifier(prefix: "ios-controller")
    private let socket = SignalingClient()
    private let store = SecureStore(service: "com.meizzo.controller.ios")

    init() {
        socket.onMessage = { [weak self] message in Task { @MainActor in self?.handle(message) } }
        socket.onStateChange = { [weak self] state in
            Task { @MainActor in
                self?.connectionState = state
                self?.connected = state == "Connected"
            }
        }
        socket.onConnected = { [weak self] in
            guard let self else { return }
            self.connectionState = "Connected"
            let ids = UserDefaults.standard.stringArray(forKey: "paired-client-ids") ?? []
            for id in ids {
                if let token = self.store.value(for: "controller-token-\(id)") {
                    self.socket.send(["type": "auth.connect", "role": "controller",
                                      "clientId": id, "authToken": token])
                }
            }
            self.socket.send(["type": "controller.list_devices"])
        }
    }

    func connect() {
        socket.connect(endpoint: endpoint)
        connectionState = "Connecting"
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
            if let token = message["authToken"] as? String, let id = message["clientId"] as? String {
                try? store.save(token, key: "controller-token-\(id)")
                var ids = UserDefaults.standard.stringArray(forKey: "paired-client-ids") ?? []
                if !ids.contains(id) { ids.append(id); UserDefaults.standard.set(ids, forKey: "paired-client-ids") }
            }
            if let id = message["clientId"] as? String {
                devices.append(MeizzoDevice(clientId: id, status: "online", lastSeen: nil, metadata: ["platform": "iOS"]))
            }
        } else if type == "controller.device_list", let raw = message["devices"] as? [[String: Any]] {
            devices = raw.compactMap { item in
                guard let id = item["clientId"] as? String else { return nil }
                if let metadata = item["metadata"] as? [String: Any],
                   let telemetry = metadata["telemetry"] as? [String: Any] {
                    telemetryByDevice[id] = telemetry.mapValues { String(describing: $0) }
                }
                return MeizzoDevice(clientId: id, status: item["status"] as? String ?? "offline",
                                    lastSeen: (item["lastSeen"] as? NSNumber)?.int64Value,
                                metadata: (item["metadata"] as? [String: Any] ?? [:])
                                    .compactMapValues { $0 as? String })
            }
        } else if type == "device.status", let id = message["clientId"] as? String,
                  let index = devices.firstIndex(where: { $0.clientId == id }) {
            devices[index].status = message["status"] as? String ?? devices[index].status
            if let metadata = message["metadata"] as? [String: Any],
               let telemetry = metadata["telemetry"] as? [String: Any] {
                telemetryByDevice[id] = telemetry.mapValues { String(describing: $0) }
            }
        }
        connectionState = socket.connectionState
    }
}
