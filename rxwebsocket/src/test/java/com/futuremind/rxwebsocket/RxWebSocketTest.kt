package com.futuremind.rxwebsocket

import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class RxWebSocketTest {

    private val realSocket : WebSocket = mockk()
    private val okHttpClient : OkHttpClient = mockk()
    private val request = Request.Builder().url("https://what.ever").build()

    private lateinit var rxSocket : RxWebSocket

    @BeforeEach
    fun setUp(){
        rxSocket = RxWebSocket(okHttpClient, request)
        every { okHttpClient.newWebSocket(request, any()) } returns realSocket
    }

    @Test
    fun `given subscribed to rxws, immediately returns Connecting Status`() {
        rxSocket.connect()
            .test()
            .assertValue{ it is SocketState.Connecting }
    }

    @Test
    fun `given subscribed to rxws, the flowable doesn't complete`() {
        rxSocket.connect()
            .test()
            .assertNotComplete()
    }

}
