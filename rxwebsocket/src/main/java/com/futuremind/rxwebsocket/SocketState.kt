package com.futuremind.rxwebsocket

import io.reactivex.Flowable
import okhttp3.WebSocket
import okio.ByteString

sealed class SocketState {

    interface SendCapable {
        fun send(text: String): Boolean
        fun send(bytes: ByteString): Boolean
    }

    object Disconnecting : SocketState()

    object Disconnected : SocketState()

    class Connecting(private val socket: WebSocket) : SendCapable, SocketState() {
        override fun send(text: String) = socket.send(text)
        override fun send(bytes: ByteString) = socket.send(bytes)
    }

    class Connected(
        private val socket: WebSocket,
        private val messageFlowable: Flowable<String>,
        private val byteMessageFlowable: Flowable<ByteString>
    ) : SendCapable, SocketState() {
        override fun send(text: String) = socket.send(text)
        override fun send(bytes: ByteString) = socket.send(bytes)
        fun messageFlowable() = messageFlowable
        fun byteMessageFlowable() = byteMessageFlowable
    }


}