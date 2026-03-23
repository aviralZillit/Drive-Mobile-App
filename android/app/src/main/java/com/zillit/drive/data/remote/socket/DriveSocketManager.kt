package com.zillit.drive.data.remote.socket

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class DriveSocketEvent(val event: String, val data: Any?)

/**
 * Manages Socket.IO connection for real-time Drive events.
 * Mirrors the web's listenerSocket.js — listens for drive:* and notification:* events.
 */
@Singleton
class DriveSocketManager @Inject constructor() {

    private var socket: Socket? = null
    private val _events = MutableSharedFlow<DriveSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<DriveSocketEvent> = _events

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect(socketUrl: String, projectId: String) {
        // Disconnect previous connection if any
        disconnect()

        try {
            val opts = IO.Options.builder()
                .setForceNew(true)
                .setTransports(arrayOf("websocket"))
                .setReconnection(true)
                .setReconnectionDelay(2000)
                .setReconnectionDelayMax(30000)
                .build()

            socket = IO.socket(socketUrl, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                // Join the project room (same as web: socket.emit('join_room', ...))
                val roomData = JSONObject().put("room", "${projectId}_room")
                socket?.emit("join_room", roomData)
            }

            // Register all drive + badge events
            ALL_EVENTS.forEach { event ->
                socket?.on(event) { args ->
                    _events.tryEmit(DriveSocketEvent(event, args.firstOrNull()))
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun disconnect() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    companion object {
        // All drive events the backend emits (matches web listenerSocket.js)
        val DRIVE_EVENTS = listOf(
            "drive:file:added", "drive:file:updated", "drive:file:deleted", "drive:file:moved",
            "drive:folder:created", "drive:folder:updated", "drive:folder:deleted", "drive:folder:moved",
            "drive:folder:shared", "drive:file:shared",
            "drive:bulk:deleted", "drive:bulk:moved",
            "drive:access:changed"
        )

        val BADGE_EVENTS = listOf(
            "notification:save",
            "notification:silent",
            "notification:level:read",
            "notification:read:sync"
        )

        val ALL_EVENTS = DRIVE_EVENTS + BADGE_EVENTS
    }
}
