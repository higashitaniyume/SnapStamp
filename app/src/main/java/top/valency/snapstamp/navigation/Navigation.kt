package top.valency.snapstamp.navigation

import androidx.camera.core.ImageCapture
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import top.valency.snapstamp.ui.screens.CameraOverlay
import top.valency.snapstamp.ui.screens.LibraryScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Camera : Screen("camera", "生成", Icons.Default.PhotoCamera)
    object Library : Screen("library", "库", Icons.Default.Collections)
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Camera, Screen.Library)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, null) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) { popUpTo(navController.graph.startDestinationId); launchSingleTop = true }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(navController: NavHostController, imageCapture: ImageCapture) {
    NavHost(navController, startDestination = Screen.Camera.route) {
        composable(Screen.Camera.route) { CameraOverlay(imageCapture) }
        composable(Screen.Library.route) { LibraryScreen() }
    }
}