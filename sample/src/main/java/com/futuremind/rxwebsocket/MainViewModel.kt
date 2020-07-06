package com.futuremind.rxwebsocket

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers;

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainViewModel : ViewModel() {

    private val okHttpClient = OkHttpClient.Builder().build()
    private val request = Request.Builder().url("wss://echo.websocket.org").build()

    private val rxSocket = RxWebSocket(okHttpClient, request)

    private val socketConnection: Flowable<SocketState> = rxSocket
        .connect()
        .retryWhen { it.delay(3, TimeUnit.SECONDS) }
        .replay(1)
        .autoConnect()

    private val outgoingMessagesProcessor = PublishProcessor.create<String>()

    private val outgoingMessagesDisposable = socketConnection
        .ofType(SocketState.SendCapable::class.java)
        .switchMap { state ->
            outgoingMessagesProcessor.doOnNext { state.send(it) }
        }
        .subscribe()

    fun observeSocketState(): Flowable<SocketState> = socketConnection

    fun observeMessages(): Flowable<String> = socketConnection
        .ofType(SocketState.Connected::class.java)
        .switchMap { it.messageFlowable() }

    fun sendMessage(message: String) = outgoingMessagesProcessor.onNext(message)

    override fun onCleared() {
        rxSocket.disconnect(1000, "")
        outgoingMessagesDisposable.dispose()
    }

}