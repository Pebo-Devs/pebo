package app.pebo.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun configurePeboHttpClient(block: HttpClientConfigBuilder = {}): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        block()
    }

typealias HttpClientConfigBuilder = io.ktor.client.HttpClientConfig<*>.() -> Unit
