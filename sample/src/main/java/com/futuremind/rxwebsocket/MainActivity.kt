package com.futuremind.rxwebsocket

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var messagesDisposable: Disposable
    private lateinit var socketStateDisposable: Disposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewModel : MainViewModel by viewModels()

        socketStateDisposable = viewModel.observeSocketState()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { socketStateTv.text = it::class.java.simpleName }

        messagesDisposable = viewModel.observeMessages()
            .scan("") { old: String, new: String -> "$new\n$old" }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { messagesTv.text = it }

        sendBtn.setOnClickListener { viewModel.sendMessage(messageEt.text.toString()) }

    }

    override fun onDestroy() {
        super.onDestroy()
        messagesDisposable.dispose()
        socketStateDisposable.dispose()
    }

}
