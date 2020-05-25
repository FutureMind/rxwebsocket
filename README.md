# RxWebSocket

[![](https://jitpack.io/v/FutureMind/rxwebsocket.svg)](https://jitpack.io/#FutureMind/rxwebsocket) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

RxWebSocket is a simple, lightweight, reactive wrapper around [OkHttp WebSocket](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-web-socket/), inspired by [RxAndroidBle](https://github.com/Polidea/RxAndroidBle).

Instead of implementing a `WebSocketListener` like you would normally do with `WebSocket`, you can subscribe to it and when it is connected, subscribe to its messages. When you're done with the connection, you can simply unsubscribe and it takes care of closing the connection for you.

## Usage

### Connecting

Simply prepare you `OkhttpClient` and a regular `okhttp3.Request`.

```kotlin
val okHttpClient = OkHttpClient.Builder().build()
val request = Request.Builder().url(...).build()

RxWebSocket(okHttpClient, request)
    .connect()
    .subscribe()

```

### Socket states

The `RxWebSocket.connect()` returns a `Flowable` with different socket states to subscribe to.

| Rx event | State | Description |
|-------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| onNext | Connecting | Returned immediately after subscribing. <br>You can start sending at this stage and the messages will be queued for when the socket connects. <br>Corresponds to the state before `onOpen` in `WebSocketListener`. |
| onNext | Connected | Returned when the socket has successfully opened connection. <br>You can send messages while in this state. <br>You can subscribe to `messageFlowable` or `byteMessageFlowable` in this state.<br>Corresponds to `onOpen` in `WebSocketListener`. |
| onNext | Disconnecting | Corresponds to `onClosing` in `WebSocketListener`. |
| onNext | Disconnected | Corresponds to `onClosed` in `WebSocketListener`. Right after this event, `onCompleted` is published. |
| onCompleted | - | The Flowable is completed right after `Disconnected` (`onClosed`). |
| onError | - | The Flowable signals `SocketConnectionException` which contains the original exception that caused the issue and appropriate `okhttp3.Response`.<br>Corresponds to `onFailure` in `WebSocketListener`. |


---

So the whole flow can look something like this.

```kotlin
RxWebSocket(okHttpClient, request)
    .connect()
    .switchMap { state ->
        when(state) {
            is SocketState.Connecting -> TODO()
            is SocketState.Connected -> TODO()
            SocketState.Disconnecting -> TODO()
            SocketState.Disconnected -> TODO()
        }
    }
    .doOnError { TODO("Handle socket connection failed") }
    .doOnComplete { TODO("Handle socket connection closed gracefully") }
    .subscribe()
```

It's good to use `switchMap` here to make sure that when the state changes, you unsubscribe from e.g. `Connected.messageFlowable`.

### Sending messages

`Connecting` and `Connected` implement `SendCapable`. In both these states you can send messages, although in `Connecting` the messages will be queued and sent when it's possible (`okhttp` exposes such mechanism).

```kotlin
RxWebSocket(okHttpClient, request)
    .connect()
    .ofType(SocketState.SendCapable::class.java)
    .flatMapCompletable { state ->
        Completable.fromCallable { state.send("Hello world") }
    }
    .subscribe()
```

### Receiving messages

```kotlin
RxWebSocket(okHttpClient, request)
    .connect()
    .switchMap { state ->
        when (state) {
            is SocketState.Connected -> state.messageFlowable()
            else -> Flowable.never()
        }
    }
    .subscribe { msg -> handleMessage(msg) }
```

### Dealing with disconnection

Because socket failures cause flowable error and graceful disconnections cause it to complete, you can leverage the power of RxJava's `retry` and `repeat` functions to implement your reconnection logic in a very elegant way.

```kotlin
RxWebSocket(okHttpClient, request)
    .connect()
    .retryWhen { it.delay(3, TimeUnit.SECONDS) }
    .subscribe()
```


## Real life example

In real life, you will probably have some `ViewModel` which observes incoming messages to pass them to UI and also accepts new messages e.g. incoming from some input field. Here's a sample implementation of such `ViewModel` (you can aso find it in `sample` directory).

```kotlin
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
```


Let's break it down piece by piece.


First, we prepare our RxWebSocket for connection.

```kotlin
private val okHttpClient = OkHttpClient.Builder().build()
private val request = Request.Builder().url("wss://echo.websocket.org").build()

private val rxSocket = RxWebSocket(okHttpClient, request)
```

---

Then we introduce our connection flowable.

```kotlin
private val socketConnection: Flowable<SocketState> = rxSocket
        .connect()
        .retryWhen { it.delay(3, TimeUnit.SECONDS) }
        .replay(1)
        .autoConnect()
```

`.replay(1).autoConnect()` is used to multicast our flowable so that our socket connetion can be easily used by different subscribers. `replay(1)` makes sure that whenever new subscriber arrives, he immediately receives the current state the socket is in.

Notice the `retryWhen` - it is used to make sure, that whenever our connection breaks it is reconnected. Of course this is a very simple example, you can use a much more sophisticated reconnection logic in your `retry` operator.

---

```kotlin
private val outgoingMessagesProcessor = PublishProcessor.create<String>()

private val outgoingMessagesDisposable = socketConnection
    .ofType(SocketState.SendCapable::class.java)
    .switchMap { state ->
        outgoingMessagesProcessor.doOnNext { state.send(it) }
    }
    .subscribe()
```

This code is responsible for processing incoming messages and sending them through the websocket.

---

This is the interface of our `ViewModel` which should be pretty self-explanatory.

```kotlin
fun observeSocketState(): Flowable<SocketState> = socketConnection

fun observeMessages(): Flowable<String> = socketConnection
    .ofType(SocketState.Connected::class.java)
    .switchMap { it.messageFlowable() }

fun sendMessage(message: String) = outgoingMessagesProcessor.onNext(message)
```

---

Last but not least, remeber to disconnect from websocket when you're done with it. Calling `disconnect` gracefully closes the socket and completes our `socketConnection`.

```kotlin
    override fun onCleared() {
        rxSocket.disconnect(1000, "")
        outgoingMessagesDisposable.dispose()
    }
```

## Installation

RxWebSocket is available on jitpack.

```gradle
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
```

```gradle
implementation 'com.github.FutureMind:rxwebsocket:1.0'
```

## License

    The MIT License

    Copyright (c) 2020 Future Mind

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.