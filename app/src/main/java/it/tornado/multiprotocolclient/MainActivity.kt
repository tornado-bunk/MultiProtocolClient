package it.tornado.multiprotocolclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import it.tornado.multiprotocolclient.ui.theme.MultiProtocolClientTheme
import it.tornado.multiprotocolclient.screens.AboutScreen
import it.tornado.multiprotocolclient.screens.ClientScreen

data class BottomNavigationItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiProtocolClientTheme {
                val navController = rememberNavController()
                MainScreen(navController = navController)
            }
        }
    }
}

@Composable
fun MainScreen(navController: NavHostController) {
    val items = listOf(
        BottomNavigationItem(
            title = "Home",
            icon = Icons.Outlined.Home,
            route = "client"
        ),
        BottomNavigationItem(
            title = "About",
            icon = Icons.Filled.Info,
            route = "about"
        )
    )

    var selectedItemIndex by remember { mutableIntStateOf(0) }

    // Request permissions at startup
    if (Build.VERSION.SDK_INT >= 33) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // Handle permission granted/denied if needed, or just let the app proceed
        }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                        label = { Text(text = item.title) },
                        selected = selectedItemIndex == index,
                        onClick = {
                            selectedItemIndex = index
                            navController.navigate(item.route)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "client") {
            composable("client") { ClientScreen(modifier = Modifier.padding(innerPadding)) }
            composable("about") { AboutScreen(modifier = Modifier.padding(innerPadding)) }
        }
    }
}

