package com.futuremind.rxwebsocket

import io.mockk.*
import okhttp3.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class RxWebSocketTest {

    private var okHttpClient: OkHttpClient = mockk()
    private var request = Request.Builder().url("https://what.ever").build()

    private val mockWebSocket : WebSocket = mockk()
    private lateinit var mockWebSocketListener: WebSocketListener

    private lateinit var rxSocket: RxWebSocket

    @BeforeEach
    fun setUp() {
        rxSocket = RxWebSocket(okHttpClient, request)

        val socketListenerCaptor = slot<WebSocketListener>()
        every { okHttpClient.newWebSocket(request, capture(socketListenerCaptor)) } answers {
            mockWebSocketListener = socketListenerCaptor.captured
            mockWebSocketListener.onOpen(mockWebSocket, prepareOkHttpResponse(200))
            every { mockWebSocketListener.onOpen(mockWebSocket, prepareOkHttpResponse(200)) } answers {
                mockWebSocketListener.onFailure(mockWebSocket, Exception(), null)
            }
            mockWebSocket
        }

        justRun { mockWebSocket.cancel() }

        val closeCodeCaptor = slot<Int>()
        val closeMsgCaptor = slot<String>()
        every { mockWebSocket.close(capture(closeCodeCaptor), capture(closeMsgCaptor)) } answers {
            val code = closeCodeCaptor.captured
            val msg = closeMsgCaptor.captured
            mockWebSocketListener.onClosing(mockWebSocket, code, msg)
            mockWebSocketListener.onClosed(mockWebSocket, code, msg)
            true
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
    fun `given disconnect called, underlying socket is closed`() {

        rxSocket.connect().test()
        rxSocket.disconnect(1000, "abc")

        verify { mockWebSocket.close(1000, "abc") }
    }

    @Test
    fun `given disconnect called, returns Disconnecting, Disconnected and then completes`() {

        val testSubscriber = rxSocket.connect().test()

        rxSocket.disconnect(1000, "")

        testSubscriber
            .assertValueAt(1, SocketState.Disconnecting)
            .assertValueAt(2, SocketState.Disconnected)
            .assertComplete()
    }

    @Test
    fun `given unsubscribed, underlying socket is canceled`() {
        rxSocket.connect().subscribe().dispose()
        verify { mockWebSocket.cancel() }
    }

    @Test
    fun `rxsw can be connected to again after being unsubscribed`() {
        rxSocket.connect().subscribe().dispose()
        rxSocket.connect().test().assertValue{ it is SocketState.Connecting }
    }

    @Test
    fun `given connection fails, observable notifies error with SocketConnectionException`() {
        val connectionException = Exception()
        val connectionResponse = prepareOkHttpResponse(500)
        makeMockWebSocketFail(connectionException, connectionResponse)
        rxSocket.connect().test().assertError { exception ->
            exception is RxSocketListener.SocketConnectionException
                    && exception.originalException == connectionException
                    && exception.response == connectionResponse
        }
    }

    private fun makeMockWebSocketFail(exception: Throwable, message: Response?) {
//        val socketListenerCaptor = slot<WebSocketListener>()
//        every { okHttpClient.newWebSocket(request, capture(socketListenerCaptor)) } answers {
//            mockWebSocketListener = socketListenerCaptor.captured
//            mockWebSocketListener.onFailure(mockWebSocket, exception, message)
//            mockWebSocket
//        }
//        every { mockWebSocketListener.onOpen(mockWebSocket, prepareOkHttpResponse(200)) } answers {
//            mockWebSocketListener.onFailure(mockWebSocket, exception, message)
//        }
    }

    private fun prepareOkHttpResponse(code: Int) = Response.Builder()
        .protocol(Protocol.HTTP_1_0)
        .request(request)
        .code(code)
        .message("")
        .build()

}
