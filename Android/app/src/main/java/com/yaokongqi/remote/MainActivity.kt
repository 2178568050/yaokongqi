package com.yaokongqi.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.MaterialTheme
import com.yaokongqi.remote.ui.MainViewModel
import com.yaokongqi.remote.ui.screens.MainScreen
import com.yaokongqi.remote.ui.theme.YaokongqiTheme

class MainActivity : ComponentActivity() {
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            val vm = mainViewModel
            val settings by vm.appSettings.collectAsState()
            YaokongqiTheme(darkTheme = settings.darkMode) {
                val view = LocalView.current
                val darkTheme = settings.darkMode
                SideEffect {
                    val window = window
                    if (!settings.shooterGamepadMode) {
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !darkTheme
                            isAppearanceLightNavigationBars = !darkTheme
                        }
                    }
                }
                MainScreen(
                    viewModel = vm,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) {
            mainViewModel.onAppForeground()
        }
    }

    override fun onPause() {
        if (::mainViewModel.isInitialized) {
            mainViewModel.onAppBackground()
        }
        super.onPause()
    }
}
