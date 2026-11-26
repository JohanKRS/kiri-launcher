package io.github.johankrs.kiri.market

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class HttpResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** The one thing the data sources need from the network, so tests can stand in for it. */
fun interface HttpFetcher {
    /** Blocking GET. Throws [IOException] when the server cannot be reached. */
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** [HttpFetcher] over the platform's own HTTP client: no library needed. */
class UrlConnectionFetcher(private val timeoutMillis: Int = 10_000) : HttpFetcher {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }
}
