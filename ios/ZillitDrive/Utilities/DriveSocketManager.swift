import Foundation
import SocketIO

/// Manages Socket.IO connection for real-time Drive events.
/// Mirrors the web's listenerSocket.js — listens for drive:* and notification:* events.
@MainActor
final class DriveSocketManager: ObservableObject {
    @Published var isConnected = false

    private var manager: SocketManager?
    private var socket: SocketIOClient?
    private var listeners: [(event: String, callback: ([Any]) -> Void)] = []

    // All drive events the backend emits (matches web listenerSocket.js)
    static let driveEvents = [
        "drive:file:added", "drive:file:updated", "drive:file:deleted", "drive:file:moved",
        "drive:folder:created", "drive:folder:updated", "drive:folder:deleted", "drive:folder:moved",
        "drive:folder:shared", "drive:file:shared",
        "drive:bulk:deleted", "drive:bulk:moved",
        "drive:access:changed",
    ]

    static let badgeEvents = [
        "notification:save",
        "notification:silent",
        "notification:level:read",
        "notification:read:sync",
    ]

    func connect(socketURL: String, projectId: String) {
        guard let url = URL(string: socketURL) else {
            print("🔌 [DriveSocket] Invalid socket URL: \(socketURL)")
            return
        }

        // Disconnect previous connection if any
        disconnect()

        // Generate moduledata for socket auth (same as web's encryptHeaders)
        guard let session = SessionManager.shared.currentSession else {
            print("🔌 [DriveSocket] No session for socket auth")
            return
        }
        let moduledata = AESCBCEncryptor.generateModuleData(
            userId: session.userId,
            projectId: session.projectId,
            deviceId: session.deviceId
        )
        print("🔌 [DriveSocket] Auth moduledata generated (\(moduledata.prefix(40))...)")

        manager = SocketManager(socketURL: url, config: [
            .log(false),
            .forceWebsockets(true),
            .reconnects(true),
            .reconnectWait(2),
            .reconnectWaitMax(30),
            .version(.three),
            .extraHeaders(["moduledata": moduledata]),
            .connectParams(["moduledata": moduledata]),
        ])
        socket = manager?.defaultSocket

        socket?.on(clientEvent: .connect) { [weak self] _, _ in
            Task { @MainActor in
                self?.isConnected = true
            }
            print("🔌 [DriveSocket] Connected — emitting user:join + joining room \(projectId)_room")
            // Web emits user:join first, then join_room
            self?.socket?.emit("user:join", [:])
            self?.socket?.emit("join_room", ["room": "\(projectId)_room"])
        }

        socket?.on(clientEvent: .disconnect) { [weak self] _, _ in
            Task { @MainActor in
                self?.isConnected = false
            }
            print("🔌 [DriveSocket] Disconnected")
        }

        socket?.on(clientEvent: .error) { data, _ in
            print("🔌 [DriveSocket] Error: \(data)")
        }

        socket?.on(clientEvent: .reconnectAttempt) { data, _ in
            print("🔌 [DriveSocket] Reconnect attempt: \(data)")
        }

        socket?.on(clientEvent: .statusChange) { data, _ in
            print("🔌 [DriveSocket] Status change: \(data)")
        }

        // Re-register stored listeners
        for listener in listeners {
            socket?.on(listener.event) { data, _ in listener.callback(data) }
        }

        socket?.connect()
    }

    func disconnect() {
        socket?.disconnect()
        socket = nil
        manager = nil
        isConnected = false
    }

    /// Register an event listener. Safe to call before connect() — listeners are stored and applied on connection.
    func on(_ event: String, callback: @escaping ([Any]) -> Void) {
        listeners.append((event: event, callback: callback))
        // If already connected, register immediately
        socket?.on(event) { data, _ in callback(data) }
    }

    /// Emit a socket event (e.g., notification:level:read for badge clearing)
    func emit(_ event: String, _ data: [String: Any]) {
        socket?.emit(event, data)
    }

    /// Emit with acknowledgement
    func emit(_ event: String, _ data: [String: Any], ack: @escaping ([Any]) -> Void) {
        socket?.emitWithAck(event, data).timingOut(after: 10) { items in
            ack(items)
        }
    }
}
