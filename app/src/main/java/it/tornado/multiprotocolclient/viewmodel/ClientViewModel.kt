package it.tornado.multiprotocolclient.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ClientViewModel : ViewModel() {
    private val _response = MutableStateFlow<List<String>>(emptyList())
    val response: StateFlow<List<String>> = _response.asStateFlow()

    fun sendHttpRequest(protocol: String, ip: String, port: String, use_ssl: Boolean, see_only_status_code: Boolean) {
        viewModelScope.launch {
            if (protocol == "HTTP") {
                val urlString = if (use_ssl) "https://$ip:$port" else "http://$ip:$port"
                httpResponse(urlString, see_only_status_code).collect { chunk ->
                    _response.value += chunk
                }
            } else {
                simulateServerResponse().collect { chunk ->
                    _response.value += chunk
                }
            }
        }
    }


    private fun httpResponse(urlString: String, see_only_status_code: Boolean) = flow {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            emit(listOf("HTTP Response Code: $responseCode"))

            if (!see_only_status_code) {
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    var line: String? = reader.readLine()
                    while (line != null) {
                        emit(listOf(line))
                        line = reader.readLine()
                    }
                    reader.close()
                    inputStream.close()
                } else {
                    emit(listOf("HTTP Error: $responseCode"))
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            emit(listOf("Error: ${e.message}"))
            Log.e("ClientViewModel", "HTTP Request Error", e)
        }
    }
        .flowOn(Dispatchers.IO)

    private fun simulateServerResponse() = flow {
        val chunks = listOf(
            "Server Response: ",
            "Connection established...",
            "Data received...",
            "Processing data...",
            "Data processed...",
            "Response ready!",
            "Connection closed."
        )
        for (chunk in chunks) {
            emit(listOf(chunk))
            delay(1000) // Simulate delay between chunks
        }
    }

    fun resetResponse() {
        _response.value = emptyList()
    }
}