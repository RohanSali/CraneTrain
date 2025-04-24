package com.example.cranetrain

import android.util.Log
import okhttp3.*
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var isConnected = false
    private var listener: WebSocketListener? = null

    companion object {
        private const val TAG = "WebSocketManager"
        private const val WEBSOCKET_URL = "ws://192.168.0.103:8888/camera-streams" // Include the path that server expects
    }

    fun connect(listener: WebSocketListener) {
        this.listener = listener
        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(WEBSOCKET_URL)
            .addHeader("Upgrade", "websocket")
            .addHeader("Connection", "Upgrade")
            .addHeader("Sec-WebSocket-Version", "13")
            .addHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw==")
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "WebSocket connection opened")
                listener.onOpen(webSocket, response)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text message: $text")
                listener.onMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                Log.d(TAG, "Received binary message: ${bytes.size} bytes")
                listener.onMessage(webSocket, bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "WebSocket closing: $code - $reason")
                listener.onClosing(webSocket, code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "WebSocket closed: $code - $reason")
                listener.onClosed(webSocket, code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "WebSocket failure: ${t.message}")
                listener.onFailure(webSocket, t, response)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Normal closure")
        client?.dispatcher?.executorService?.shutdown()
        isConnected = false
    }
}