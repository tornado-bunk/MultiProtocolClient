package it.tornado.androidntpclient.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ClientViewModel : ViewModel() {
    var text by mutableStateOf("Welcome to the Client Screen")
        private set

    fun updateText(newText: String) {
        text = newText
    }
}
