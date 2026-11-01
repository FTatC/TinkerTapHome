package io.github.ftatc.tinkertaphome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.ftatc.tinkertaphome.ui.devices.DeviceListScreen
import io.github.ftatc.tinkertaphome.ui.permissions.BlePermissionGate
import io.github.ftatc.tinkertaphome.ui.theme.TinkerTapTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TinkerTapTheme {
                BlePermissionGate {
                    DeviceListScreen()
                }
            }
        }
    }
}
