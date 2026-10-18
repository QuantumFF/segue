package com.quantumff.segue.core.playback

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.media3.session.MediaButtonReceiver
import androidx.media3.session.MediaLibraryService
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quantumff.segue.core.playback.bluetooth.BluetoothAutoplayReceiver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackServiceManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `playback service is discoverable as a media library service`() {
        val services = context.packageManager.queryIntentServices(
            Intent(MediaLibraryService.SERVICE_INTERFACE).setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        )
        assertThat(services.map { it.serviceInfo.name }).contains(SeguePlaybackService::class.java.name)
    }

    @Test
    fun `playback service is a media playback foreground service`() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, SeguePlaybackService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    @Test
    fun `the app declares itself an Android Auto media app`() {
        val app = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertThat(app.metaData.getInt("com.google.android.gms.car.application")).isEqualTo(R.xml.automotive_app_desc)
    }

    @Test
    fun `browse artwork is served by a private provider that can grant reads`() {
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.artwork", 0)!!
        assertThat(provider.exported).isFalse()
        assertThat(provider.grantUriPermissions).isTrue()
    }

    @Test
    fun `media buttons reach the service when it is not running`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        )
        assertThat(receivers.map { it.activityInfo.name }).contains(MediaButtonReceiver::class.java.name)
    }

    @Test
    fun `Bluetooth audio connections reach the autoplay receiver`() {
        val receivers = context.packageManager.queryBroadcastReceivers(
            Intent(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED).setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        )
        assertThat(receivers.map { it.activityInfo.name }).contains(BluetoothAutoplayReceiver::class.java.name)
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
        assertThat(requested).asList().contains(Manifest.permission.BLUETOOTH_CONNECT)
    }
}
