package it.tornado.multiprotocolclient

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
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
import it.tornado.multiprotocolclient.screens.ConsoleFullScreenScreen
import it.tornado.multiprotocolclient.screens.ProtocolPickerScreen
import it.tornado.multiprotocolclient.viewmodel.ClientViewModel

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
            MultiProtocolClientTheme(dynamicColor = false) {
                val navController = rememberNavController()
                MainScreen(navController = navController)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun MainScreen(navController: NavHostController) {
    val clientViewModel: ClientViewModel = viewModel()
    val items = listOf(
        BottomNavigationItem(
            title = "Home",
            icon = Icons.Outlined.Home,
            route = "protocol_picker"
        ),
        BottomNavigationItem(
            title = "About",
            icon = Icons.Filled.Info,
            route = "about"
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "protocol_picker"
    val showFloatingBar = currentRoute == "protocol_picker"
    val bottomBarScrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

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
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(bottomBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "protocol_picker",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("protocol_picker") {
                    ProtocolPickerScreen(
                        modifier = Modifier,
                        onOpenRequestBuilder = { protocol ->
                            navController.navigate("request_builder/${Uri.encode(protocol)}")
                        }
                    )
                }
                composable("request_builder/{protocol}") { backStackEntry ->
                    val protocolArg = backStackEntry.arguments?.getString("protocol").orEmpty()
                    ClientScreen(
                        modifier = Modifier,
                        viewModel = clientViewModel,
                        initialProtocol = protocolArg,
                        showProtocolPickerInline = false,
                        onChangeProtocolRequested = {
                            val returnedToPicker = navController.popBackStack("protocol_picker", inclusive = false)
                            if (!returnedToPicker) {
                                navController.navigate("protocol_picker") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onOpenFullscreenConsole = {
                            navController.navigate("console_fullscreen")
                        }
                    )
                }
                composable("console_fullscreen") {
                    ConsoleFullScreenScreen(
                        viewModel = clientViewModel,
                        onBackToRequest = { navController.popBackStack() }
                    )
                }
                composable("about") {
                    AboutScreen(modifier = Modifier)
                }
            }

            if (showFloatingBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 210.dp, max = 300.dp),
                        shape = RoundedCornerShape(38.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
                    ) {
                        FlexibleBottomAppBar(
                            modifier = Modifier
                                .clip(RoundedCornerShape(38.dp)),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement,
                            expandedHeight = 60.dp,
                            scrollBehavior = bottomBarScrollBehavior
                        ) {
                            items.forEach { item ->
                                val selected = if (item.route == "protocol_picker") {
                                    currentRoute != "about"
                                } else {
                                    currentRoute.startsWith(item.route)
                                }
                                val contentColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = tween(durationMillis = 220),
                                    label = "bottom_item_content"
                                )
                                val containerColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                    animationSpec = tween(durationMillis = 220),
                                    label = "bottom_item_container"
                                )

                                TextButton(
                                    onClick = {
                                        if (!currentRoute.startsWith(item.route)) {
                                            navController.navigate(item.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                            }
                                        }
                                    },
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = containerColor,
                                        contentColor = contentColor
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

