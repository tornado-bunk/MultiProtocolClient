package it.tornado.multiprotocolclient.protocol.http

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpHandler {
    private data class ParsedHttpTarget(
        val host: String,
        val pathWithQuery: String
    )

    // Function to handle HTTP requests
    fun processHttpRequest(request: HttpRequest) = flow {
        if (request.protocol == "HTTP") {
            val target = parseHttpTarget(request.ip)
            if (target.host.isBlank()) {
                emit(listOf("Error: Invalid host value"))
                return@flow
            }

            // Create URL string based on request parameters
            val urlString = if (request.useSSL) {
                "https://${target.host}:${request.port}${target.pathWithQuery}"
            } else {
                "http://${target.host}:${request.port}${target.pathWithQuery}"
            }

            try {
                // Open connection to the URL
                val url = URL(urlString)
                var connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection) {
                    if (request.trustSelfSigned) {
                        try {
                            val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
                            object : X509TrustManager {
                                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                                @SuppressLint("TrustAllX509TrustManager")
                                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                @SuppressLint("TrustAllX509TrustManager")
                                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                            })

                            val sc = SSLContext.getInstance("SSL")
                            sc.init(null, trustAllCerts, java.security.SecureRandom())
                            connection.sslSocketFactory = sc.socketFactory
                            
                            val allHostsValid = HostnameVerifier { _, _ -> true }
                            connection.hostnameVerifier = allHostsValid
                            
                            emit(listOf("Trusting all SSL certificates...\n"))
                        } catch (e: Exception) {
                            emit(listOf("Error setting up SSL trust: ${e.message}"))
                        }
                    }
                }

                // Set connection parameters
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // Getting response code
                val responseCode = connection.responseCode
                emit(listOf("HTTP Response Code: $responseCode"))

                // If the request is successful, read the response if seeOnlyStatusCode is false
                if (!request.seeOnlyStatusCode) {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { reader ->
                            reader.lineSequence().forEach { line ->
                                emit(listOf(line))
                            }
                        }
                    } else {
                        emit(listOf("HTTP Error: $responseCode"))
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                emit(listOf("Error: ${e.message}"))
                Log.e("HttpHandler", "HTTP Request Error", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseHttpTarget(input: String): ParsedHttpTarget {
        val raw = input.trim()
        if (raw.isBlank()) return ParsedHttpTarget("", "/")

        val candidate = if (raw.contains("://")) raw else "http://$raw"
        return try {
            val uri = URI(candidate)
            val hostCandidate = when {
                !uri.host.isNullOrBlank() -> uri.host
                !uri.authority.isNullOrBlank() -> uri.authority
                else -> raw
            }
            val sanitizedHost = sanitizeHost(hostCandidate)
            val path = uri.rawPath.takeUnless { it.isNullOrBlank() } ?: "/"
            val queryPart = uri.rawQuery?.let { "?$it" } ?: ""
            ParsedHttpTarget(sanitizedHost, path + queryPart)
        } catch (_: Exception) {
            val noScheme = raw.substringAfter("://", raw)
            val authority = noScheme
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
            val pathAndQuery = noScheme.removePrefix(authority)
                .substringBefore("#")
                .let { if (it.isBlank()) "/" else it }
            ParsedHttpTarget(sanitizeHost(authority), pathAndQuery)
        }
    }

    private fun sanitizeHost(authorityOrHost: String): String {
        return authorityOrHost
            .substringAfterLast("@")
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .removeSuffix(".")
            .let { value ->
                if (value.startsWith("[") && value.contains("]")) value else value.substringBefore(":")
            }
            .trim()
    }

}
