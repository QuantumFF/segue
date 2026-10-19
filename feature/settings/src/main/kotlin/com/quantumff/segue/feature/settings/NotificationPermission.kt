package com.quantumff.segue.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Whether Segue may post notifications. From Android 13 that takes a runtime permission. */
internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
