package com.quantumff.segue.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
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

/** UiAutomator injects one move per 5 ms: 25 steps is a brisk swipe of about 125 ms. */
private const val TAB_SWIPE_STEPS = 25

/** The Library's tabs, in order, as the tab row labels them. */
private val LIBRARY_TABS = listOf("Albums", "Artists", "Songs", "Playlists", "Genres", "Downloads")

/** Iterations per benchmark configuration. */
const val BENCHMARK_ITERATIONS = 10

/** What every benchmark compares: no AOT compilation against the shipped Baseline Profile. */
val comparedCompilationModes: List<CompilationMode>
    get() = listOf(CompilationMode.None(), CompilationMode.Partial(BaselineProfileMode.Require))
private const val GESTURE_MARGIN_FRACTION = 0.2f

/**
 * The package under test. The release-based variants the plugin builds (nonMinifiedRelease,
 * benchmarkRelease) keep the release application ID; `targetAppId` overrides it.
 */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.quantumff.segue"

private fun argument(name: String, default: String): String =
    InstrumentationRegistry.getArguments().getString(name) ?: default

private fun UiDevice.waitFor(selector: BySelector, timeout: Long = UI_TIMEOUT_MS): UiObject2 =
    checkNotNull(wait(Until.findObject(selector), timeout)) { "Timed out waiting for $selector" }

/**
 * Starts the app and, if it shows the login screen, signs in to the demo server and waits until
 * the album grid has content (the first library sync). Leaves the app running, signed in, on
 * whatever screen it opened (Home after a fresh sign-in).
 */
fun MacrobenchmarkScope.signInIfNeeded() {
    // A notification shade left open (emulator boot notices) hides the app from UiAutomator.
    device.executeShellCommand("cmd statusbar collapse")
    startActivityAndWait()
    val connect = By.text("Connect")
    val home = By.text("Home")
    device.wait(Until.hasObject(By.text(Pattern.compile("Connect|Home|Library"))), UI_TIMEOUT_MS)
    if (device.hasObject(By.text("Library"))) return
    val fields = device.wait(Until.findObjects(By.clazz("android.widget.EditText")), UI_TIMEOUT_MS)
    check(fields != null && fields.size >= 3) {
        val shown = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
        "Login fields not found. Window hierarchy:\n$shown"
    }
    fields[0].text = argument("segue.server", "https://demo.navidrome.org")
    fields[1].text = argument("segue.user", "demo")
    fields[2].text = argument("segue.password", "demo")
    // Setting text through accessibility shows no keyboard, so the button stays on screen.
    device.waitFor(connect).click()
    // Either straight to Home, or the first-sync screen with a Continue button.
    device.wait(Until.hasObject(By.text("Continue").enabled(true)), LOGIN_TIMEOUT_MS)
    device.findObject(By.text("Continue"))?.click()
    device.waitFor(home, LOGIN_TIMEOUT_MS)
    openLibraryAlbums()
    device.waitFor(By.textEndsWith(" albums"), SYNC_TIMEOUT_MS)
    device.waitFor(home).click()
}

/** Signs in outside any measured or recorded block: call it from a test's `@Before`. */
fun signInBeforeRecording() {
    MacrobenchmarkScope(targetPackage, launchWithClearTask = true).run {
        signInIfNeeded()
        killProcess()
    }
}

/** Library tab, Albums. */
fun MacrobenchmarkScope.openLibraryAlbums() {
    device.waitFor(By.text("Library")).click()
    device.waitFor(By.text("Albums")).click()
    device.waitForIdle()
}

/**
 * The album grid: the tallest scrollable on the Library screen (the tab and chip rows scroll too).
 * The tab pager, if listed, is as tall: take the innermost, which comes last in hierarchy order.
 */
fun MacrobenchmarkScope.albumGrid(): UiObject2 {
    val scrollables = device.wait(Until.findObjects(By.scrollable(true)), UI_TIMEOUT_MS)
    val grid = scrollables?.withIndex()?.maxWithOrNull(compareBy({ it.value.visibleBounds.height() }, { it.index }))
    return checkNotNull(grid?.value) { "No album grid" }
}

/** Flings the album grid down and back up. */
fun MacrobenchmarkScope.scrollAlbumGrid() {
    fun fling(direction: Direction) {
        // Re-found each time: Compose replaces the grid's accessibility node as it scrolls.
        val grid = albumGrid()
        // Keep clear of the system gesture areas.
        grid.setGestureMargin((device.displayWidth * GESTURE_MARGIN_FRACTION).toInt())
        grid.fling(direction)
        device.waitForIdle()
    }
    repeat(SCROLL_STEPS) { fling(Direction.DOWN) }
    repeat(SCROLL_STEPS) { fling(Direction.UP) }
}

/**
 * From Albums, swipes through the Library's tabs to Downloads and back to Albums, waiting for each
 * tab to be selected. Swipes run along the filter row, never over the Songs list, whose rows queue
 * a song when swiped; on Downloads, which has no filter row, the same line crosses the top of its
 * page.
 */
fun MacrobenchmarkScope.swipeLibraryTabs() {
    // The album count sits on the filter row's centre line.
    val y = device.waitFor(By.textEndsWith(" albums")).visibleBounds.centerY()
    // Inside the system gesture areas' margins, so no swipe reads as Back.
    val left = (device.displayWidth * GESTURE_MARGIN_FRACTION).toInt()
    val right = device.displayWidth - left
    fun swipeTo(index: Int, startX: Int, endX: Int) {
        device.swipe(startX, y, endX, y, TAB_SWIPE_STEPS)
        device.waitFor(By.text(LIBRARY_TABS[index]).selected(true))
        device.waitForIdle()
    }
    for (index in 1..LIBRARY_TABS.lastIndex) swipeTo(index, startX = right, endX = left)
    for (index in LIBRARY_TABS.lastIndex - 1 downTo 0) swipeTo(index, startX = left, endX = right)
}

/**
 * Taps the floating "Play albums" pill over the album grid: every album is queued whole and starts
 * playing. Then opens and closes the Shuffle pill's queue options menu.
 */
fun MacrobenchmarkScope.playLibraryAlbums() {
    device.waitFor(By.desc("Play albums")).click()
    device.waitFor(By.desc("Pause"), PLAYBACK_TIMEOUT_MS)
    device.waitFor(By.desc("Shuffle albums: queue options")).click()
    device.waitFor(By.text("Add to queue"))
    device.pressBack()
    device.waitForIdle()
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

/** Opens Now Playing from the mini player, then its Queue panel, then goes back to the album. */
fun MacrobenchmarkScope.openNowPlayingAndQueue() {
    // The lowest Pause button is the mini player's; tap the mini player left of it.
    val pause = device.findObjects(By.desc("Pause")).maxBy { it.visibleBounds.centerY() }
    device.click(device.displayWidth / 3, pause.visibleBounds.centerY())
    device.waitFor(By.text("Queue")).click()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
}
