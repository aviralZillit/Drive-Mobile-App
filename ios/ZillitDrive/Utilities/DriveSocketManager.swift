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
            #if DEBUG
            print("[DriveSocket] Invalid socket URL: \(socketURL)")
            #endif
            return
        }

        // Disconnect previous connection if any
        disconnect()

        manager = SocketManager(socketURL: url, config: [
            .log(false),
            .forceWebsockets(true),
            .reconnects(true),
            .reconnectWait(2),
            .reconnectWaitMax(30),
        ])
        socket = manager?.defaultSocket

        socket?.on(clientEvent: .connect) { [weak self] _, _ in
            Task { @MainActor in
                self?.isConnected = true
                #if DEBUG
                print("[DriveSocket] Connected — joining room \(projectId)_room")
                #endif
            }
            // Join the project room (same as web: socket.emit('join_room', ...))
            self?.socket?.emit("join_room", ["room": "\(projectId)_room"])
        }

        socket?.on(clientEvent: .disconnect) { [weak self] _, _ in
            Task { @MainActor in
                self?.isConnected = false
                #if DEBUG
                print("[DriveSocket] Disconnected")
                #endif
            }
        }

        socket?.on(clientEvent: .error) { data, _ in
            #if DEBUG
            print("[DriveSocket] Error: \(data)")
            #endif
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
