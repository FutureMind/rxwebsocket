package com.futuremind.rxwebsocket

import androidx.lifecycle.ViewModel
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import okhttp3.OkHttpClient
import okhttp3.Request

class MainViewModel : ViewModel() {

    private val okHttpClient = OkHttpClient.Builder().build()
    private val request = Request.Builder().url("wss://echo.websocket.org").build()

    private val rxSocket = RxWebSocket(okHttpClient, request)

    private val socketConnection: Flowable<SocketState> = rxSocket
        .connect()
        .replay(1)
        .autoConnect()

    private val outgoingMessagesProcessor = PublishProcessor.create<String>()

    private val outgoingMessagesDisposable = socketConnection
        .ofType(SocketState.SendCapable::class.java)
        .subscribe()

    fun sendMessage(message: String) {

    }

    fun observeMessages(): Flowable<String> {
        TODO()
    }

    override fun onCleared() {
        super.onCleared()
        rxSocket.disconnect(1000, "")
    }

}