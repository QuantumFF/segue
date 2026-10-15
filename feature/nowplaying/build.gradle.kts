plugins {
    alias(libs.plugins.segue.android.feature)
}

dependencies {
    // Now Playing drives the player directly: transport, seek, modes, sleep timer.
    implementation(projects.core.playback)
}
