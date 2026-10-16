# Performance and battery

Results and how to reproduce them. All numbers come from the `segue_pixel`-class emulator
(API 37 google_apis x86_64, Pixel 8 profile, host-GPU rendering) against the Navidrome demo
server. An emulator is not a phone: compare the numbers with each other, not with a device.

## Startup

`timeToInitialDisplayMs` / `TotalTime`, signed in, launching to Home. "No profile" is
`CompilationMode.None()`; "Baseline Profile" is `CompilationMode.Partial(BaselineProfileMode.Require)`
(what an install with the bundled profile gets).

| Run | Start | No profile | Baseline Profile |
|-----|-------|-----------:|-----------------:|
| Macrobenchmark, loaded host, before the startup fixes (10 iterations) | Cold | 839 ms | 634 ms (−24%) |
| | Warm | 93 ms | 97 ms |
| Macrobenchmark, idle host, final code (10 iterations, three runs) | Cold | 291 / 368 / 378 ms | 463 / 442 / 447 ms |
| | Warm | 66 / 77 ms | 48 / 79 ms |
| `am start -W`, idle host, final code, modes interleaved (16 starts each) | Cold | 303 ms | 262 ms (−14%) |

The emulator is noisy: with the host busy (other emulators and Gradle builds) interpretation is
expensive and the profile cuts cold start by a quarter. On an idle host the Macrobenchmark
profile runs came out consistently slower than no profile (three runs), while plain `am start`
cold starts with the modes interleaved (which cancels drift between them) show −14%. The
emulator cannot settle this; measure on a phone before trusting any of these.

Startup fixes found with StrictMode (below): the `OkHttpClient` (TLS setup, about 50 ms) was
built on the main thread while Hilt injected the `Application`; the downloads directory and the
stream cache were opened on the main thread. A debug cold start now logs no violations. (The
stream cache is opened on an IO thread at app start; a service that starts before that finishes
waits for it rather than opening it again.)

The release build carries both profiles: `assets/dexopt/baseline.prof`, and the startup profile
applied by R8 as DEX layout (`build/intermediates/merged_startup_profile`; startup classes go to
`classes.dex`). The baselineprofile plugin enables this by default with R8; nothing in
`app/build.gradle.kts` needed changing.

## Album grid scroll jank

`ScrollBenchmarks` measures `FrameTimingMetric` while flinging the album grid. The emulator does
not record SurfaceFlinger's frame timeline, so the metric finds no frames there; run it on a
phone. On the emulator, `dumpsys gfxinfo` over the same flings (3 down, 3 up, median of 5 runs each):

| Host | Profile | Janky frames | p50 | p90 | p99 |
|------|---------|-------------:|----:|----:|----:|
| Loaded | No  | 9.6% | 27 ms | 46 ms | 200 ms |
| Loaded | Yes | 9.8% | 16 ms | 44 ms | 125 ms |
| Idle   | No  | 4.9% | 21 ms | 32 ms | 150 ms |
| Idle   | Yes | 5.9% | 16 ms | 32 ms | 150 ms |

The emulator renders through the host GPU, so most of the jank is the emulator's. Read these as
"no regression, lower median frame time with the profile", not as a measured jank reduction.

## Compose

- Compiler reports (debug compilations only, opt-in):
  `./gradlew assembleDebug -Psegue.composeReports=true`, then read
  `<module>/build/compose-reports/*-composables.txt` and `*-classes.txt`. With strong skipping
  every composable in the hot paths (rows, cards, mini player, Now Playing) is skippable; the UI
  models in `core:model` are stable through `compose_compiler_config.conf`.
- Lists and grids use stable keys and `contentType`; cover art requests the `getCoverArt` size
  bucket for its drawn size.
- Position ticker (500 ms), measured by temporarily adding
  `SideEffect { Log.d("Recomp", name) }` to the composables below in a debug build, then
  counting log lines while playing for 20 s:

  | Surface | Before | After |
  |---------|-------:|------:|
  | Mini player (progress line is read in draw) | 0 | 0 |
  | Now Playing screen, controls, seekbar | 0 | 0 |
  | Now Playing elapsed-time text | 38 | 20 |

  The time texts now recompose once per shown second (`derivedStateOf`) instead of on every
  tick, the expanded player bar's elapsed text likewise, and the seekbar's accessibility
  semantics follow the position samples instead of the per-frame glide.

## Battery audit

Release build, playing an album from the demo server, stop-when-paused set to 5 minutes.

| Check | Evidence | Result |
|-------|----------|--------|
| Wake locks while playing | `dumpsys power`: `ExoPlayer:WakeLockManager` (partial) and audioserver's `AudioMix` | Expected |
| Wake locks while paused | `dumpsys power`: `Wake Locks: size=0` | None |
| Wake locks after stop-when-paused | `size=0`; service no longer foreground or started (`startRequested=false`) | None |
| Wi-Fi lock | Was held from play to pause (`WAKE_MODE_NETWORK`; Android 14 turns it into a low-latency lock, which disables Wi-Fi power save). Now held only while ExoPlayer loads: 2.5 s at the start of a track, then released while it plays from the buffer | **Fixed** |
| Network while playing a buffered/prefetched track | `dumpsys netstats --poll` per-UID bytes over 60 s mid-track: 0 B rx, 0 B tx | Radio idle |
| Scrobbles and queue sync | Code: one timer per track for the scrobble point (`Scrobbler`), sends batched through a network-constrained WorkManager job; queue uploads debounced (`DebouncedQueueWriter`) and event-driven; the position ticker only runs while a position is on screen | As specified |
| Periodic work and alarms | `dumpsys jobscheduler`: no registered jobs while playing (history shows only the app-start library syncs); `dumpsys alarm`: no entries for the app | None |
| WorkManager constraints | Library sync started offline sits in `jobscheduler` as `NET BATNOTLOW`, unsatisfied connectivity, and runs when Wi-Fi returns. Downloads: unmetered/connected plus optional charging from settings; scrobbles: connected | As specified |
| Audio offload, screen off | The emulator's audio HAL has only mixer outputs (`dumpsys media.audio_flinger`), so it cannot offload. `ScreenOffOffloadTest` checks the switch: offload enabled (gapless required) on screen off, default on screen on, when starting with the screen off, and never while ReplayGain boost is on | Verify on a phone |
| Foreground service timeout | Foreground while playing; paused for 5 min, the service stops itself and leaves the foreground | As specified |

Unverified on the emulator: actual DSP offload and offload sleep (needs a phone: play, turn the
screen off, then `adb shell dumpsys media.audio_flinger | grep -i offload` should show a
`COMPRESS_OFFLOAD` output thread), and real radio power states.

## StrictMode

Debuggable builds enable thread and VM policies with `penaltyLog()` (`DebugStrictMode`). Read
them with `adb logcat -s StrictMode`. After the fixes above, a cold start, browsing, opening an
album, playing, and Now Playing log no violations.

## Release build

R8 full mode (`android.enableR8.fullMode=true`), resource shrinking on. A smoke test of the
release APK (sign in, browse, search, artist, album, playback, media notification, foreground
service) logged no `ClassNotFoundException`, serialization, or Hilt errors, so no keep rules
were needed.

| APK | Size |
|-----|-----:|
| Release before | 8,687,234 B (8.3 MiB) |
| Release with Baseline Profile and startup DEX layout (final) | 9,129,660 B (8.7 MiB) |

The extra 0.4 MiB is the profile (`assets/dexopt/baseline.prof`) and the startup-ordered second
DEX file.

## Reproduce

Use one device or emulator and point Gradle at it with `ANDROID_SERIAL`. Agents already have it
set to their own emulator (`scripts/agent-emulator` boots it) and skip the export.

```sh
export ANDROID_SERIAL=<serial from adb devices>

# Regenerate the Baseline Profile and startup profile
# (writes app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt)
./gradlew :app:generateBaselineProfile

# Run the benchmarks (startup cold/warm and album grid scroll, each without and with the profile)
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
# One class only:
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.quantumff.segue.baselineprofile.StartupBenchmarks
```

The journeys sign in to `https://demo.navidrome.org` (`demo`/`demo`) when the app shows the login
screen. Use another server with
`-Pandroid.testInstrumentationRunnerArguments.segue.server=…` (and `segue.user`,
`segue.password`). The selectors use English UI text. Results land in
`baselineprofile/build/outputs/connected_android_test_additional_output/`.
