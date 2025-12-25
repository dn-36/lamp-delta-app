package com.example.lampappdelta

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import cafe.adriel.voyager.navigator.Navigator
import com.example.lampappdelta.detail_screen.DetailScreen
import com.example.lampappdelta.main_screen.MainScreen
import com.example.lampappdelta.ui.theme.LampAppDeltaTheme

object Navigation{

    var navigator: Navigator? = null

}


class MainActivity : ComponentActivity() {

    private val permissions = buildList {
        // Bluetooth
        add(Manifest.permission.BLUETOOTH)          // legacy / normal (на новых версиях не runtime)
        add(Manifest.permission.BLUETOOTH_SCAN)     // Android 12+
        add(Manifest.permission.BLUETOOTH_CONNECT)  // Android 12+
        add(Manifest.permission.ACCESS_FINE_LOCATION)

        /*// Location (по твоей логике)
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> { // < Android 10
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> { // Android 10+
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            else -> {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } */
    }.distinct().toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        BluetoothSearch.init(applicationContext)

        // Отключаем автоматическое размещение контента под системные окна:
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Скрываем системные панели (status + navigation):
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // Поведение: показать панели по свайпу, затем они снова автоматически скрываются
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            LampAppDeltaTheme {

                Navigator(

                    screen = MainScreen()

                )

            }
        }

        requestPermissions()

    }

    private fun requestPermissions() {
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    private fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LampAppDeltaTheme {
        Greeting("Android")
    }
}