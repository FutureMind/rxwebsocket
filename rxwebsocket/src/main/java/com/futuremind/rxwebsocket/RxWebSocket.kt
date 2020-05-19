package com.futuremind.rxwebsocket

import io.reactivex.BackpressureStrategy.LATEST
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.processors.PublishProcessor
import okhttp3.*
import okio.ByteString
import java.net.SocketException


class RxWebSocket(
    private val webSocketFactory: WebSocket.Factory,
    private val request: Request
) {

    private var socket: WebSocket? = null

    fun connect(): Flowable<SocketState> = Flowable
        .create(::openSocketAndListen, LATEST)
        .doFinally { socket?.cancel(); socket = null }

    fun disconnect(code: Int, message: String) {
        socket?.close(code, message)
    }

    private fun openSocketAndListen(emitter: FlowableEmitter<SocketState>) {
        val listener = RxSocketListener(emitter)
        val socket = webSocketFactory.newWebSocket(request, listener)
        this.socket = socket
        listener.onCreate(socket)
    }

}

class RxSocketListener(private val stateEmitter: FlowableEmitter<SocketState>) : WebSocketListener() {

    private val textMsgProcessor = PublishProcessor.create<String>()
    private val byteMsgProcessor = PublishProcessor.create<ByteString>()

    /**
     * Additional state. According to the [WebSocket] docs, messages can be enqueued even before the
     * socket is open, hence we let users do it upon socket creation.
     */
    fun onCreate(socket: WebSocket) {
        stateEmitter.onNext(SocketState.Connecting(socket))
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        val connectedState = SocketState.Connected(webSocket, textMsgProcessor, byteMsgProcessor)
        stateEmitter.onNext(connectedState)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        textMsgProcessor.offer(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        byteMsgProcessor.offer(bytes)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        stateEmitter.tryOnError(SocketConnectionException(t, response))
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        stateEmitter.onNext(SocketState.Disconnecting)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        stateEmitter.onNext(SocketState.Disconnected)
        stateEmitter.onComplete()
    }

    class SocketConnectionException(val originalException: Throwable, val response: Response?) : SocketException()

}