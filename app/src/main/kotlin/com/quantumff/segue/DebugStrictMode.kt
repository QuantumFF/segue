package com.quantumff.segue

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode

/**
 * Debuggable builds only: logs main-thread disk and network access and leaked resources
 * (`adb logcat -s StrictMode`). Log-only, so a violation never crashes a debug session.
 */
internal object DebugStrictMode {
    fun enableIfDebuggable(context: Context) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) detectUnsafeIntentLaunch() }
                .penaltyLog()
                .build(),
        )
    }
}
