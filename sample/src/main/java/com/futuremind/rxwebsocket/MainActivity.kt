package com.futuremind.rxwebsocket

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : AppCompatActivity() {

    private lateinit var socketConnection : Flowable<SocketState>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val okHttpClient = OkHttpClient.Builder().build()
        val request = Request.Builder().url("wss://echo.websocket.org").build()

        /*
        The piece of code below does the following:
        1. Initiates connection.
        2. Notifies "Connecting" state.
        3. Connects and notifies "Connected" state.
        4. Sends "abc" message.
        5. Starts listening to incoming messages.
        6. Receives "abc" message from echo server.
        7. Upon receiving first message, completes the observable, which triggers disconnection.
         */

        RxWebSocket(okHttpClient, request).connect()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { showMessage(it::class.java.simpleName) }
            .switchMap { state ->
                if (state is SocketState.SendCapable) {
                    state.send("abc")
                }
                when (state) {
                    is SocketState.Connected -> state.messageFlowable()
                    else -> Flowable.never()
                }
            }
            .firstOrError() // just load one message and finish
            .observeOn(AndroidSchedulers.mainThread())
            .doAfterTerminate { showMessage("Disconnected and terminated") }
            .subscribe(
                { showMessage("Message received: $it") },
                { showMessage(it.message ?: ""); it.printStackTrace() }
            )

    }

    private fun showMessage(message: String) {
        Log.v("Socket msg", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
