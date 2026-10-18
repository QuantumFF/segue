package com.quantumff.segue.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Whether Segue hears Bluetooth audio devices connect. From Android 12 that takes the Nearby
 * devices permission; before, the install-time Bluetooth permission is enough.
 */
internal fun hasBluetoothConnectPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
