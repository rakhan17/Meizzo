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
    @Published public private(set) var lastError: String?
    public var onMessage: (([String: Any]) -> Void)?
    private var task: URLSessionWebSocketTask?
    private var url: URL?

    public func connect(endpoint: String) {
        guard let endpointURL = URL(string: endpoint) else {
            lastError = "Enter a valid ws:// or wss:// endpoint."
            return
        }
        url = endpointURL
        let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
        task = session.webSocketTask(with: endpointURL)
        task?.resume()
        receive()
    }

    public func disconnect() {
        task?.cancel(with: .normalClosure, reason: nil)
        connected = false
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
                    self.lastError = error.localizedDescription
                }
            }
        }
    }

    public func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                           didOpenWithProtocol protocol: String?) {
        DispatchQueue.main.async { self.connected = true; self.lastError = nil }
    }

    public func urlSession(_ session: URLSession, task: URLSessionTask,
                           didCompleteWithError error: Error?) {
        if let error { DispatchQueue.main.async { self.connected = false; self.lastError = error.localizedDescription } }
    }
}

public func randomIdentifier(prefix: String) -> String {
    "\(prefix)-\(UUID().uuidString.lowercased())"
}

public func randomPairingCode() -> String {
    String(format: "%06d", Int.random(in: 0...999999))
}
