package com.yaokongqi.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
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
                MainScreen(viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) {
            mainViewModel.onAppForeground()
        }
    }
}
