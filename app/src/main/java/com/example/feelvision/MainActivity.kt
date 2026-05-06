package com.feelvision

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.feelvision.data.settings.SettingsRepository
import com.feelvision.hardware.ButtonEventSource
import com.feelvision.hardware.PhoneCameraSource
import com.feelvision.ui.navigation.NavGraph
import com.feelvision.ui.theme.FeelVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var buttonSource: ButtonEventSource
    @Inject lateinit var cameraSource: PhoneCameraSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request necessary permissions on startup
        val permissions = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (android.os.Build.VERSION.SDK_INT < 33) {
             // For older versions
             permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
             permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        requestPermissions(permissions.toTypedArray(), 101)

        // Android 11+ (API 30+) Scoped Storage / Manage All Files Access request
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }

        setContent {
            FeelVisionTheme {
                val navController = rememberNavController()
                val debugEnabled by settings.debugEnabled.collectAsStateWithLifecycle(false)
                NavGraph(navController = navController, showDebug = debugEnabled, cameraSource = cameraSource)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (buttonSource.onKeyDown(keyCode)) true
        else super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        runBlocking {
            if (buttonSource.onKeyUp(keyCode)) true
            else super.onKeyUp(keyCode, event)
        }
}