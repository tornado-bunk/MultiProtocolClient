// HttpHandler.kt
package it.tornado.multiprotocolclient.protocol.http

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpHandler {
    // Function to handle HTTP requests
    fun processHttpRequest(request: HttpRequest) = flow {
        if (request.protocol == "HTTP") {
            // Create URL string based on request parameters
            val urlString = if (request.useSSL) "https://${request.ip}:${request.port}"
            else "http://${request.ip}:${request.port}"

            try {
                // Open connection to the URL
                val url = URL(urlString)
                var connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection && !request.trustSelfSigned) {
                    emit(listOf("Verifying SSL certificate...\n"))
                    try {
                        connection.connect()
                    } catch (e: Exception) {
                        emit(listOf("SSL Certificate Error: ${e.message}"))
                        return@flow
                    }
                } else if (connection is HttpsURLConnection) {
                    val trustingConnection = createTrustingConnection(url)
                    connection.disconnect()
                    connection = trustingConnection
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

    // Function to create a trusting connection
    private fun createTrustingConnection(url: URL): HttpURLConnection {
        // Create a TrustManager that trusts all certificates
        val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        })

        // Create an SSLContext that uses the TrustManager
        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

        // Create a HostnameVerifier that trusts all hosts
        val allHostsValid = HostnameVerifier { _, _ -> true }
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)

        return url.openConnection() as HttpURLConnection
    }
}
