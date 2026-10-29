package http.ktor

internal actual fun platformEngine(): io.ktor.client.engine.HttpClientEngine =
    io.ktor.client.engine.winhttp.WinHttp
        .create()
