package it.tornado.multiprotocolclient.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel

@Composable
fun ClientScreen(viewModel: ClientViewModel = viewModel()) {
    val text = viewModel.text
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = text)
        Button(onClick = { viewModel.updateText("Updated text on Client Screen") }) {
            Text("Update Text")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClientScreenPreview() {
    ClientScreen()
}
