---
name: emulator
description: >-
  Run Segue on the agents' own Android emulator: install a build, reproduce a bug, tap through
  the app, take screenshots, run connected tests or benchmarks. Use before any adb or on-device
  Gradle task.
---

# Agents' emulator

The user runs Segue on their own emulator (`segue_pixel`) or phone while you work, signed in to
their real Navidrome server, and agents in other worktrees run their own emulators. Each checkout
gets its own copy of the `segue_agent` emulator, and this session's `ANDROID_SERIAL` already points
at it: `adb` and Gradle's install and connected tasks target it, so leave the device unspecified.
A hook refuses commands that name any other device. Subagents in this session share its emulator.

## Steps

1. **Boot**: `scripts/agent-emulator` prints the serial once the emulator has booted. It reuses a
   running one, and every fresh boot starts from a clean state. If it reports that the machine is
   at its emulator limit, wait for another worktree's emulator to stop, or ask the user.
2. **Install**: `./gradlew :app:installDebug`, then
   `adb shell am start -n com.quantumff.segue.debug/com.quantumff.segue.MainActivity`.
3. **Sign in** only to `https://demo.navidrome.org` as `demo`/`demo`. Read the server field on the
   login screen before connecting.
4. **Drive**: `scripts/ui-dump` lists each labelled element as `text | content-desc | x,y`; tap with
   `adb shell input tap x y`. Read the dump before taking a screenshot; take one
   (`adb exec-out screencap -p > shot.png`) only when the question is visual.
5. **Stop**: `scripts/agent-emulator stop` when you are done.

Done when every on-device claim you report rests on a dump or screenshot from this emulator.
