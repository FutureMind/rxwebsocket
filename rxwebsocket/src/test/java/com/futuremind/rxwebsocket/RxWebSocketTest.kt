package com.futuremind.rxwebsocket

import io.mockk.*
import io.reactivex.Completable
import io.reactivex.Flowable
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class RxWebSocketTest {

    private var okHttpClient: OkHttpClient = mockk()
    private var request = Request.Builder().url("https://what.ever").build()

    private val mockWebSocket: WebSocket = mockk()
    private lateinit var mockWebSocketListener: WebSocketListener

    private lateinit var rxSocket: RxWebSocket

    @BeforeEach
    fun setUp() {
        rxSocket = RxWebSocket(okHttpClient, request)

        val socketListenerCaptor = slot<WebSocketListener>()
        every { okHttpClient.newWebSocket(request, capture(socketListenerCaptor)) } answers {
            mockWebSocketListener = socketListenerCaptor.captured
            mockWebSocket
        }

        justRun { mockWebSocket.cancel() }
        every { mockWebSocket.send(any<String>()) } returns true
        every { mockWebSocket.send(any<ByteString>()) } returns true

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
            .assertValueAt(0) { it is SocketState.Connecting }
    }

    @Test
    fun `given underlying socket connects, returns Connected status`() {
        rxSocket.connect()
            .mockSuccessfulConnection()
            .test()
            .assertValueAt(1) { it is SocketState.Connected }
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
        rxSocket.connect().test().assertValueAt(0) { it is SocketState.Connecting }
    }

    @Test
    fun `given connection fails, observable notifies error with SocketConnectionException`() {
        val connectionException = Exception()
        val connectionResponse = prepareOkHttpResponse(500)
        rxSocket.connect()
            .mockFailedConnection(connectionException, connectionResponse)
            .test()
            .assertError { exception ->
                exception is RxSocketListener.SocketConnectionException
                        && exception.originalException == connectionException
                        && exception.response == connectionResponse
            }
    }

    @Test
    fun `given text messages sent to rxws, they are sent through the underlying socket`() {
        rxSocket.connect()
            .switchMapCompletable { state ->
                if(state is SocketState.SendCapable){
                    state.send("abc")
                    state.send("def")
                }
                Completable.complete()
            }
            .subscribe()

        verify { mockWebSocket.send("abc") }
        verify { mockWebSocket.send("def") }
    }

    @Test
    fun `given byte string messages sent to rxws, they are sent through the underlying socket`() {
        rxSocket.connect()
            .switchMapCompletable { state ->
                if(state is SocketState.SendCapable){
                    state.send("abc".encodeUtf8())
                    state.send("def".encodeUtf8())
                }
                Completable.complete()
            }
            .subscribe()

        verify { mockWebSocket.send("abc".encodeUtf8()) }
        verify { mockWebSocket.send("def".encodeUtf8()) }
    }

    @Test
    fun `given text messages received by underlying socket, they are pushed through rxws`() {

        val testSubscriber = rxSocket.connect()
            .mockSuccessfulConnection()
            .switchMap { state ->
                when(state){
                    is SocketState.Connected -> state.messageFlowable()
                    else -> Flowable.never()
                }
            }
            .test()

        mockWebSocketListener.onMessage(mockWebSocket, "abc")
        mockWebSocketListener.onMessage(mockWebSocket, "def")

        testSubscriber.assertValues("abc", "def")

    }

    @Test
    fun `given bytestring messages received by underlying socket, they are pushed through rxws`() {

        val testSubscriber = rxSocket.connect()
            .mockSuccessfulConnection()
            .switchMap { state ->
                when(state){
                    is SocketState.Connected -> state.byteMessageFlowable()
                    else -> Flowable.never()
                }
            }
            .test()

        mockWebSocketListener.onMessage(mockWebSocket, "abc".encodeUtf8())
        mockWebSocketListener.onMessage(mockWebSocket, "def".encodeUtf8())

        testSubscriber.assertValues("abc".encodeUtf8(), "def".encodeUtf8())

    }

    @Test
    fun `given bytestring message received by underlying socket, regular text message is not pushed through rxws`() {

        val testSubscriber = rxSocket.connect()
            .mockSuccessfulConnection()
            .switchMap { state ->
                when(state){
                    is SocketState.Connected -> state.messageFlowable()
                    else -> Flowable.never()
                }
            }
            .test()

        mockWebSocketListener.onMessage(mockWebSocket, "abc".encodeUtf8())

        testSubscriber.assertNever("abc")

    }

    @Test
    fun `given regular text message received by underlying socket, bytestring message is not pushed through rxws`() {

        val testSubscriber = rxSocket.connect()
            .mockSuccessfulConnection()
            .switchMap { state ->
                when(state){
                    is SocketState.Connected -> state.byteMessageFlowable()
                    else -> Flowable.never()
                }
            }
            .test()

        mockWebSocketListener.onMessage(mockWebSocket, "abc")

        testSubscriber.assertNever("abc".encodeUtf8())

    }

    private fun prepareOkHttpResponse(code: Int) = Response.Builder()
        .protocol(Protocol.HTTP_1_0)
        .request(request)
        .code(code)
        .message("")
        .build()

    private fun <T> Flowable<T>.mockSuccessfulConnection(): Flowable<T> = this.doOnNext { state ->
        if (state is SocketState.Connecting) {
            mockWebSocketListener.onOpen(mockWebSocket, prepareOkHttpResponse(200))
        }
    }

    private fun <T> Flowable<T>.mockFailedConnection(
        exception: Throwable,
        message: Response?
    ): Flowable<T> = this.doOnNext { state ->
        if (state is SocketState.Connecting) {
            mockWebSocketListener.onFailure(mockWebSocket, exception, message)
        }
    }

}
