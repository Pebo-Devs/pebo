package app.pebo.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DesktopOAuthRedirectReceiver(
    private val path: String = "/cb",
) : AutoCloseable {
    private val future = CompletableFuture<OAuthRedirectResult>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val redirectUri: String = "http://127.0.0.1:${server.address.port}$path"

    init {
        server.createContext(path) { exchange -> handle(exchange) }
        server.executor = null
        server.start()
    }

    suspend fun await(timeoutMillis: Long = 120_000): OAuthRedirectResult = withContext(Dispatchers.IO) {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } finally {
            close()
        }
    }

    override fun close() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
        val result = when {
            query["error"] != null -> OAuthRedirectResult.Error(
                error = query.getValue("error"),
                description = query["error_description"],
                state = query["state"],
            )
            query["code"] != null && query["state"] != null -> OAuthRedirectResult.Code(
                code = query.getValue("code"),
                state = query.getValue("state"),
            )
            else -> OAuthRedirectResult.Error(
                error = "invalid_redirect",
                description = "OAuth redirect did not contain code/state or error.",
                state = query["state"],
            )
        }
        future.complete(result)
        val body = when (result) {
            is OAuthRedirectResult.Code -> "Pebo sign-in complete. You can close this window."
            is OAuthRedirectResult.Error -> "Pebo sign-in failed: ${result.error}"
        }
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = decode(pair.substring(0, idx))
            val value = decode(pair.substring(idx + 1))
            key to value
        }.toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
