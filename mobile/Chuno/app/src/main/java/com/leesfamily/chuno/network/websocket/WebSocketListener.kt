package com.leesfamily.chuno.network.websocket

import android.util.Log
import com.google.gson.Gson
import com.leesfamily.chuno.network.data.AllRoomList
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketListener(messageListener: MessageListener) : WebSocketListener() {
    private var messageListener: MessageListener

    init {
        this.messageListener = messageListener
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.d(TAG, "onClosed: ")
        WebSocketManager.setIsConnect(false)
        messageListener.onClose()
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "onClosing: ")
        WebSocketManager.setIsConnect(false)
        messageListener.onClose()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.e(TAG, "onFailure: ")
        if (response != null) {
            Log.i(
                TAG,
                "connect failed：" + response.message
            )
        }
        Log.i(
            TAG,
            "connect failed throwable：" + t.message
        )
        WebSocketManager.setIsConnect(false)
        messageListener.onConnectFailed()
        WebSocketManager.reconnect()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.d(TAG, "onMessage:  text $text")
        messageListener.onMessage(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.d(TAG, "onMessage: ")
        messageListener.onMessage(bytes.base64())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        Log.d(TAG, "onOpen: ")
        WebSocketManager.setWebSocket(webSocket)
        WebSocketManager.setIsConnect(response.code == 101)
        if (!WebSocketManager.isConnect()) {
            WebSocketManager.reconnect()
        } else {
            Log.i(TAG, "connect success.")
            messageListener.onConnectSuccess()
        }
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val TAG = "추노_WebSocketListener"
    }
}