# Segue

A modern, minimalist Android client for [Navidrome](https://www.navidrome.org/), built on the
Subsonic API with OpenSubsonic extensions. Goals: a rich feature set, excellent performance, and
good battery life.

Status: early scaffold. See [docs/PLAN.md](docs/PLAN.md) for the roadmap and
[docs/DESIGN.md](docs/DESIGN.md) for the UI spec.

## Building

Requirements: JDK 17 to 21 for Gradle (tests run on a JDK 21 toolchain), Android SDK with platform 36.

```sh
export JAVA_HOME=/path/to/jdk-21
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug            # debug APK
./gradlew testDebugUnitTest test   # unit tests (Android and JVM modules)
./gradlew spotlessApply            # format (ktlint + Compose rules)
./gradlew assembleRelease          # R8-minified release, signed with the debug key for now
```

## Modules

| Module | Purpose |
| --- | --- |
| `app` | Entry point, navigation host, Hilt application |
| `core:model` | Plain Kotlin domain models |
| `core:common` | Coroutine dispatchers and app scope |
| `core:network` | Subsonic/OpenSubsonic client |
| `core:database` | Room entities, DAOs, migrations |
| `core:data` | Repositories, library sync |
| `core:datastore` | Settings |
| `core:designsystem` | Theme, tokens, shared components |
| `core:playback` | Media3 `MediaLibraryService`, queue, shuffle, scrobbling |
| `core:testing` | Shared test utilities |
| `feature:*` | Screens: home, library, album, artist, playlist, search, nowplaying, queue, settings, login, downloads |
| `baselineprofile` | Baseline Profile generator |

## License

Segue is free software, licensed under the GNU General Public License v3.0 or later.
See [LICENSE](LICENSE).
