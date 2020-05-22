# RxWebSocket


RxWebSocket is a simple, lightweight, reactive wrapper around [OkHttp WebSocket](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-web-socket/), inspired by [RxAndroidBle](https://github.com/Polidea/RxAndroidBle).

Instead of implementing a `WebSocketListener` like you would normally do with `WebSocket`, you can subscribe to it and when it is connected, subscribe to its messages. When you're done with the connection, you can simply unsubscribe and it takes care of closing the connection for you.

## Usage

### Connecting

Simply prepare you `OkhttpClient` and a regular `okhttp3.Request`.

```
val okHttpClient = OkHttpClient.Builder().build()
val request = Request.Builder().url("wss://echo.websocket.org").build()

RxWebSocket(okHttpClient, request)
    .connect()
    .subscribe()

```

### Socket states

The `RxWebSocket.connect()` returns a `Flowable` with different socket states to subscribe to.

| Rx event | State | Description |
|-------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| onNext | Connecting | Returned immediately after subscribing.<br>You can start sending at this stage and the messages will be queued for when the socket connects.<br>Corresponds to the state before `onOpen` in `WebSocketListener`. |
| onNext | Connected | Returned when the socket has successfully opened connection. <br>You can send messages while in this state.<br>You can subscribe to `messageFlowable` or `byteMessageFlowable` in this state.<br>Corresponds to `onOpen` in `WebSocketListener`. |
| onNext | Disconnecting | Corresponds to `onClosing` in `WebSocketListener`. |
| onNext | Disconnected | Corresponds to `onClosed` in `WebSocketListener`. Right after this event, `onCompleted` is published. |
| onCompleted | - | The Flowable is completed right after `Disconnected` (`onClosed`). |
| onError | - | The Flowable signals `SocketConnectionException` which contains the original exception that caused the issue and appropriate `okhttp3.Response`.<br>Corresponds to `onFailure` in `WebSocketListener`. |


---

So the whole flow can look something like this.

```
RxWebSocket(okHttpClient, request)
    .connect()
    .switchMap { state ->
        when(state) {
            SocketState.Disconnecting -> TODO()
            SocketState.Disconnected -> TODO()
            is SocketState.Connecting -> TODO()
            is SocketState.Connected -> TODO()
        }
    }
    .subscribe(
        { TODO() },
        { TODO() }
    )
```

It's good to use `switchMap` here to make sure that when the state changes, you unsubscribe from e.g. `Connected.messageFlowable`.

### Sending messages

`Connecting` and `Connected` implement `SendCapable`. In both these states you can send messages, although in `Connecting` the messages will be queued and sent when it's possible (`okhttp` exposes such mechanism).

```
RxWebSocket(okHttpClient, request)
    .connect()
    .ofType(SocketState.SendCapable::class.java)
    .flatMapCompletable { state ->
        Completable.fromCallable { state.send("Hello world") }
    }
    .subscribe()
```

### Receiving messages

```
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

## Real life scenario

TODO