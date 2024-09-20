package com.leesfamily.chuno.network.websocket

import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import java.util.concurrent.TimeUnit

object WebSocketManager {
    private val TAG = "추노_WebSocketManager"
    private const val MAX_NUM = 5  // Maximum number of reconnections
    private const val MILLIS = 5000  // Reconnection interval, milliseconds
    private lateinit var client: OkHttpClient
    private lateinit var request: Request
    private lateinit var messageListener: MessageListener
    private lateinit var mWebSocket: WebSocket
    private var isConnect = false
    private var connectNum = 0

    fun init(url: String, _messageListener: MessageListener) {
        client = OkHttpClient.Builder()
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        request = Request.Builder().url(url).build()
        messageListener = _messageListener
    }

    fun setWebSocket(webSocket: WebSocket) {
        this.mWebSocket = webSocket
    }

    /**
     * connect
     */
    fun connect() {
        if (isConnect()) {
            Log.i(TAG, "web socket connected")
            return
        }
        client.newWebSocket(request, createListener())
    }

    /**
     * Reconnection
     */
    fun reconnect() {
        if (connectNum <= MAX_NUM) {
            try {
                Thread.sleep(MILLIS.toLong())
                connect()
                connectNum++
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        } else {
            Log.i(
                TAG,
                "reconnect over $MAX_NUM,please check url or network"
            )
        }
    }

    /**
     * Whether to connect
     */
    fun isConnect(): Boolean {
        return isConnect
    }

    /**
     * send messages
     *
     * @param text string
     * @return boolean
     */
    fun sendMessage(text: String): Boolean {
        return if (!isConnect()) false else mWebSocket.send(text)
    }

    /**
     * send messages
     *
     * @param byteString character set
     * @return boolean
     */
    fun sendMessage(byteString: ByteString): Boolean {
        return if (!isConnect()) false else mWebSocket.send(byteString)
    }

    /**
     * Close connection
     */
    fun close() {
        if (isConnect()) {
            mWebSocket.cancel()
            val result = mWebSocket.close(1001, "The client actively closes the connection ")
            if (result) {
                Log.d(TAG, "close: success")
            }else{
                Log.d(TAG, "close: failed")
            }
        }
    }

    fun makeRoom(roomId: String, nickname: String, level: String): Boolean {
        val text =
            "{\"event\": \"make\", \"room\": ${roomId}, \"nickname\": ${nickname}, \"level\": \"${level}\"}"
        return sendMessage(text)
    }

    fun enterRoom(roomId: String): Boolean {
        val text =
            "{\"event\": \"getAllUserInRoom\", \"room\": ${roomId}}"
        return sendMessage(text)
    }

    fun leaveRoom(roomId: String, nickname: String, level: String): Boolean {
        val text =
            "{\"event\": \"leave\", \"room\": ${roomId}, \"nickname\": ${nickname}, \"level\": \"${level}\"}"
        return sendMessage(text)
    }

    fun submitMessage(roomId: String, nickname: String, level: String, msg: String): Boolean {
        val text =
            "{\"event\": \"chat\", \"room\": ${roomId}, \"nickname\": ${nickname}, \"level\": \"${level}\", \"msg\": \"${msg}\"}"
        return sendMessage(text)
    }

    fun readyRoom(roomId: String, nickname: String, level: String, msg: String): Boolean {
        val text =
            "{\"event\": \"ready\", \"room\": ${roomId}, \"nickname\": ${nickname}, \"level\": \"${level}\"}"
        return sendMessage(text)
    }

    fun exterminateRoom(roomId: String, nickname: String, level: String, msg: String): Boolean {
        val text =
            "{\"event\": \"clear\", \"room\": ${roomId}, \"nickname\": ${nickname}, \"level\": \"${level}\"}"
        return sendMessage(text)
    }

    fun getAllRoom(): Boolean {
        val text =
            "{\"event\": \"getAllRoom\"}"
        return sendMessage(text)
    }

    fun setIsConnect(isConnect: Boolean) {
        this.isConnect = isConnect
    }

    private fun createListener(): WebSocketListener {
        Log.d(TAG, "createListener: ")
        return WebSocketListener(messageListener)
    }
}