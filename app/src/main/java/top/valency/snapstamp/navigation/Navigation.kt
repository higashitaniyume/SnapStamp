package top.valency.snapstamp.navigation

import androidx.annotation.StringRes
import androidx.camera.core.Camera
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
import androidx.compose.ui.res.stringResource // 必须导入这个
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import top.valency.snapstamp.ui.screens.CameraOverlay
import top.valency.snapstamp.ui.screens.LibraryScreen
import top.valency.snapstamp.R

// 使用 @StringRes 确保传入的是有效的字符串资源 ID
sealed class Screen(val route: String, @StringRes val title: Int, val icon: ImageVector) {
    object Camera : Screen("camera", R.string.nav_camera_lable, Icons.Default.PhotoCamera)
    object Library : Screen("library", R.string.nav_library_lable, Icons.Default.Collections)
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Camera, Screen.Library)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                // 这里是修改点：使用 stringResource 解析 ID
                label = { Text(text = stringResource(id = screen.title)) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // 弹出到起始站点，避免堆栈积累
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // 避免多次点击同一个按钮重复创建实例
                            launchSingleTop = true
                            // 恢复之前的状态
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    imageCapture: ImageCapture,
    camera: Camera?
) {
    NavHost(navController, startDestination = Screen.Camera.route) {
        composable(Screen.Camera.route) {
            CameraOverlay(imageCapture, camera)
        }
        composable(Screen.Library.route) {
            LibraryScreen()
        }
    }
}