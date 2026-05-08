package com.feelvision

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import android.hardware.usb.UsbManager
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
    @Inject lateinit var luckfoxBridge: com.feelvision.hardware.LuckfoxBridge

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

        // Auto-start Luckfox server if already plugged in on app launch
        checkAndStartLuckfoxServer()
    }

    private val usbReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action ||
                UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                
                val device = intent.getParcelableExtra<android.hardware.usb.UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    val vendorId = device.vendorId
                    val productId = device.productId
                    if (vendorId == 0x2207 || vendorId == 8711 || 
                        (vendorId == 0x0525 && productId == 0xa4a2) || 
                        (vendorId == 1317 && productId == 42146)) {
                        
                        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                            android.util.Log.d("MainActivity", "Luckfox attached via broadcast! Starting server...")
                            val serverState = luckfoxBridge.status.value.state
                            if (serverState == com.feelvision.luckfox.LuckfoxTcpServer.ServerState.STOPPED || 
                                serverState == com.feelvision.luckfox.LuckfoxTcpServer.ServerState.ERROR) {
                                luckfoxBridge.start()
                            }
                        } else {
                            android.util.Log.d("MainActivity", "Luckfox detached via broadcast! Stopping server...")
                            luckfoxBridge.stop()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usbReceiver)
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            android.util.Log.d("MainActivity", "USB Device Attached Intent received!")
            checkAndStartLuckfoxServer()
        }
    }

    private fun checkAndStartLuckfoxServer() {
        val usbManager = getSystemService(android.content.Context.USB_SERVICE) as? UsbManager ?: return
        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            val vendorId = device.vendorId
            val productId = device.productId
            // Check if it's Luckfox board (vendor 0x2207 / 8711 or Netchip 0x0525 / 1317 with product 0xa4a2 / 42146)
            if (vendorId == 0x2207 || vendorId == 8711 || 
                (vendorId == 0x0525 && productId == 0xa4a2) || 
                (vendorId == 1317 && productId == 42146)) {
                
                val serverState = luckfoxBridge.status.value.state
                if (serverState == com.feelvision.luckfox.LuckfoxTcpServer.ServerState.STOPPED || 
                    serverState == com.feelvision.luckfox.LuckfoxTcpServer.ServerState.ERROR) {
                    android.util.Log.d("MainActivity", "Luckfox matching USB device detected but server is in state $serverState. Starting server automatically...")
                    luckfoxBridge.start()
                }
                break
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