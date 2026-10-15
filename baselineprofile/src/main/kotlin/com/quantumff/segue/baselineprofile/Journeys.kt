package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * The critical user journeys shared by the profile generator and the benchmarks. Selectors use the
 * English UI text and content descriptions, so run on an English-locale device.
 *
 * The server defaults to the public Navidrome demo. Override it with instrumentation arguments:
 * -Pandroid.testInstrumentationRunnerArguments.segue.server=https://… (also segue.user, segue.password).
 */

private const val LOGIN_TIMEOUT_MS = 30_000L
private const val SYNC_TIMEOUT_MS = 120_000L
private const val UI_TIMEOUT_MS = 10_000L
private const val PLAYBACK_TIMEOUT_MS = 20_000L
private const val SCROLL_STEPS = 3

/** The package under test, set by the baseline profile Gradle plugin for the target variant. */
val targetPackage: String
    get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) {
        "targetAppId not passed; run through the baselineprofile Gradle tasks"
    }

private fun argument(name: String, default: String): String =
    InstrumentationRegistry.getArguments().getString(name) ?: default

private fun UiDevice.waitFor(selector: BySelector, timeout: Long = UI_TIMEOUT_MS): UiObject2 =
    checkNotNull(wait(Until.findObject(selector), timeout)) { "Timed out waiting for $selector" }

/**
 * Signs in to the demo server if the app shows the login screen, then waits until the album grid
 * has content (the first library sync). Leaves the app on Home.
 */
fun MacrobenchmarkScope.signInIfNeeded() {
    startActivityAndWait()
    val connect = By.text("Connect")
    val home = By.text("Home")
    device.wait(Until.hasObject(By.text(Pattern.compile("Connect|Home"))), UI_TIMEOUT_MS)
    if (device.hasObject(home)) return
    val fields = device.wait(Until.findObjects(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)
    check(fields != null && fields.size >= 3) { "Login fields not found" }
    fields[0].text = argument("segue.server", "https://demo.navidrome.org")
    fields[1].text = argument("segue.user", "demo")
    fields[2].text = argument("segue.password", "demo")
    device.pressBack() // Hide the keyboard so the button is on screen.
    device.waitFor(connect).click()
    // Either straight to Home, or the first-sync screen with a Continue button.
    device.wait(Until.hasObject(By.text("Continue").enabled(true)), LOGIN_TIMEOUT_MS)
    device.findObject(By.text("Continue"))?.click()
    device.waitFor(home, LOGIN_TIMEOUT_MS)
    openLibraryAlbums()
    device.waitFor(By.textEndsWith(" albums"), SYNC_TIMEOUT_MS)
    device.pressBack()
}

/** Library tab, Albums. */
fun MacrobenchmarkScope.openLibraryAlbums() {
    device.waitFor(By.text("Library")).click()
    device.waitFor(By.text("Albums")).click()
    device.waitForIdle()
}

/** The album grid: the tallest scrollable on the Library screen (the tab and chip rows scroll too). */
fun MacrobenchmarkScope.albumGrid(): UiObject2 {
    val scrollables = device.wait(Until.findObjects(By.scrollable(true)), UI_TIMEOUT_MS)
    return checkNotNull(scrollables?.maxByOrNull { it.visibleBounds.height() }) { "No album grid" }
}

/** Flings the album grid down and back up. */
fun MacrobenchmarkScope.scrollAlbumGrid() {
    val grid = albumGrid()
    // Keep clear of the system gesture areas.
    grid.setGestureMargin(device.displayWidth / 5)
    repeat(SCROLL_STEPS) {
        grid.fling(Direction.DOWN)
        device.waitForIdle()
    }
    repeat(SCROLL_STEPS) {
        grid.fling(Direction.UP)
        device.waitForIdle()
    }
}

/** Opens the first album in the grid and starts it. */
fun MacrobenchmarkScope.openAlbumAndPlay() {
    val grid = albumGrid()
    // The first card's title, below the filter chips and the album count.
    val chipsBottom = device.waitFor(By.textEndsWith(" albums")).visibleBounds.bottom
    val firstTitle = grid.findObjects(By.clazz("android.widget.TextView"))
        .firstOrNull { it.visibleBounds.top > chipsBottom }
    checkNotNull(firstTitle) { "No album in the grid" }.click()
    device.waitFor(By.desc("Shuffle")) // The album hero's actions.
    device.waitFor(By.desc("Play")).click()
    // The mini player appears with a Pause button once the track plays.
    device.waitFor(By.desc("Pause"), PLAYBACK_TIMEOUT_MS)
}

/** Opens Now Playing from the mini player, then its queue, then goes back to the album. */
fun MacrobenchmarkScope.openNowPlayingAndQueue() {
    // The lowest Pause button is the mini player's; tap the mini player left of it.
    val pause = device.findObjects(By.desc("Pause")).maxBy { it.visibleBounds.centerY() }
    device.click(device.displayWidth / 3, pause.visibleBounds.centerY())
    device.waitFor(By.text("Up next")).click()
    device.wait(Until.findObject(By.text("Open queue")), UI_TIMEOUT_MS)?.click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}

/** Stops playback so later iterations start from a quiet player. */
fun MacrobenchmarkScope.stopPlayback() {
    device.executeShellCommand("am force-stop $packageName")
}
