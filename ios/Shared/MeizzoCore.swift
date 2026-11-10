import Foundation
import Combine
import Security

public struct MeizzoDevice: Identifiable, Codable, Equatable {
    public let clientId: String
    public var status: String
    public var lastSeen: Int64?
    public var metadata: [String: String]
    public var id: String { clientId }
    public var displayName: String { metadata["alias"] ?? metadata["model"] ?? clientId }
}

public final class SecureStore {
    private let service: String
    public init(service: String) { self.service = service }

    public func save(_ value: String, key: String) throws {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
        SecItemDelete(query as CFDictionary)
        var item = query
        item[kSecValueData as String] = data
        let result = SecItemAdd(item as CFDictionary, nil)
        guard result == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(result)) }
    }

    public func value(for key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

public final class SignalingClient: NSObject, ObservableObject, URLSessionWebSocketDelegate {
    @Published public private(set) var connected = false
    @Published public private(set) var connectionState = "Disconnected"
    @Published public private(set) var lastError: String?
    public var onMessage: (([String: Any]) -> Void)?
    public var onConnected: (() -> Void)?
    public var onStateChange: ((String) -> Void)?
    public var heartbeatPayload: (() -> [String: Any])?
    private var task: URLSessionWebSocketTask?
    private var url: URL?
    private var session: URLSession?
    private var reconnectWorkItem: DispatchWorkItem?
    private var reconnectAttempt = 0
    private var intentionalDisconnect = false

    public func connect(endpoint: String) {
        guard let endpointURL = URL(string: endpoint) else {
            lastError = "Enter a valid ws:// or wss:// endpoint."
            return
        }
        url = endpointURL
        intentionalDisconnect = false
        reconnectWorkItem?.cancel()
        connectionState = "Connecting"
        onStateChange?("Connecting")
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        self.session = session
        task = session.webSocketTask(with: endpointURL)
        task?.resume()
        receive()
    }

    public func disconnect() {
        intentionalDisconnect = true
        reconnectWorkItem?.cancel()
        task?.cancel(with: .normalClosure, reason: nil)
        DispatchQueue.main.async {
            self.connected = false
            self.connectionState = "Disconnected"
            self.onStateChange?("Disconnected")
        }
    }

    public func send(_ payload: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let text = String(data: data, encoding: .utf8) else { return }
        task?.send(.string(text)) { [weak self] error in
            if let error { DispatchQueue.main.async { self?.lastError = error.localizedDescription } }
        }
    }

    private func receive() {
        task?.receive { [weak self] result in
            guard let self else { return }
            switch result {
            case .success(.string(let text)):
                if let data = text.data(using: .utf8),
                   let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    DispatchQueue.main.async { self.onMessage?(object) }
                }
                self.receive()
            case .success:
                self.receive()
            case .failure(let error):
                DispatchQueue.main.async {
                    self.connected = false
                    self.connectionState = "Reconnecting"
                    self.onStateChange?("Reconnecting")
                    self.lastError = error.localizedDescription
                }
                self.scheduleReconnect()
            }
        }
    }

    private func scheduleReconnect() {
        guard !intentionalDisconnect, let url else { return }
        reconnectAttempt = min(reconnectAttempt + 1, 6)
        let delay = pow(2.0, Double(reconnectAttempt))
        let work = DispatchWorkItem { [weak self] in self?.connect(endpoint: url.absoluteString) }
        reconnectWorkItem = work
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    private func startHeartbeat() {
        guard let task else { return }
        task.sendPing { [weak self] error in
            if let error {
                DispatchQueue.main.async {
                    self?.connected = false
                    self?.connectionState = "Reconnecting"
                    self?.lastError = error.localizedDescription
                }
                self?.scheduleReconnect()
                return
            }
            self?.send(["type": "heartbeat", "telemetry": self?.heartbeatPayload?() ?? [:]])
            DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self] in
                guard let self, self.task === task else { return }
                self.startHeartbeat()
            }
        }
    }

    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                           didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async {
            self.connected = true
            self.connectionState = "Connected"
            self.onStateChange?("Connected")
            self.reconnectAttempt = 0
            self.lastError = nil
            self.startHeartbeat()
            self.onConnected?()
        }
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask,
                           didCompleteWithError error: Error?) {
        if let error {
            DispatchQueue.main.async {
                self.connected = false
                self.connectionState = "Reconnecting"
                self.onStateChange?("Reconnecting")
                self.lastError = error.localizedDescription
            }
            scheduleReconnect()
        }
    }
}

public func randomIdentifier(prefix: String) -> String {
    "\(prefix)-\(UUID().uuidString.lowercased())"
}

public func randomPairingCode() -> String {
    String(format: "%06d", Int.random(in: 0...999999))
}
