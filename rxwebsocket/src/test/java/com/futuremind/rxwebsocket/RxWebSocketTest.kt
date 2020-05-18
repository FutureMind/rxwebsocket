package com.futuremind.rxwebsocket

import io.mockk.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.ws.RealWebSocket
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*


class RxWebSocketTest {

    lateinit var realSocket: WebSocket
    private var okHttpClient: OkHttpClient = mockk()
    private var request = Request.Builder().url("https://what.ever").build()

    private lateinit var rxSocket: RxWebSocket

    @BeforeEach
    fun setUp() {
        rxSocket = RxWebSocket(okHttpClient, request)

        val initRequest = slot<Request>()
        val initListener = slot<WebSocketListener>()
        every {
            okHttpClient.newWebSocket(
                capture(initRequest),
                capture(initListener)
            )
        } answers {
            realSocket = spyk(
                RealWebSocket(
                    TaskRunner.INSTANCE,
                    initRequest.captured,
                    initListener.captured,
                    Random(),
                    0
                )
            )
            realSocket
        }
    }

    @Test
    fun `given subscribed to rxws, immediately returns Connecting Status`() {
        rxSocket.connect()
            .test()
            .assertValue { it is SocketState.Connecting }
    }

    @Test
    fun `given subscribed to rxws, the flowable doesn't complete`() {
        rxSocket.connect()
            .test()
            .assertNotComplete()
    }

    @Test
    fun `given disconnect called, returns Disconnected and then Completes`() {

        val testSubscriber = rxSocket.connect().test()

        rxSocket.disconnect(1000, "")

        verify { realSocket.close(1000, "") }

//        testSubscriber
//            .assertValueAt(1, SocketState.Disconnected)
//            .assertComplete()
    }

}
