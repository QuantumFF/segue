#!/usr/bin/env bash
# Keeps agents off the user's devices. The user runs the app on segue_pixel (and maybe a phone)
# while agents work; agents get their own emulator, whose serial is ANDROID_SERIAL in
# .claude/settings.json. adb and Gradle already default to it, so this only refuses a Bash command
# that reaches for some other device.
set -euo pipefail

cmd=$(jq -r '.tool_input.command // empty')
case $cmd in *adb* | *emulator* | *gradlew* | *ANDROID_SERIAL*) ;; *) exit 0 ;; esac

agent=$(jq -r .env.ANDROID_SERIAL "$CLAUDE_PROJECT_DIR/.claude/settings.json")

refuse() {
  echo "Blocked: $1. The user is on their own emulator or phone right now. Use the agents'" \
    "emulator ($agent): scripts/agent-emulator boots it, and adb and Gradle already target it." \
    "See .claude/skills/emulator/SKILL.md." >&2
  exit 2
}

[[ $cmd == *segue_pixel* ]] && refuse "segue_pixel is the user's emulator"

rest=$cmd
while [[ $rest =~ (emulator-[0-9]+)(.*) ]]; do
  [[ ${BASH_REMATCH[1]} == "$agent" ]] || refuse "${BASH_REMATCH[1]} is not the agents' emulator"
  rest=${BASH_REMATCH[2]}
done

# Regexes live in variables: ; & | inside [[ =~ ]] would end the expression. adb_opts is adb plus
# its own options, so a flag after the subcommand (`adb shell ls -t`) does not count.
adb_opts='adb([[:space:]]+-[[:alpha:]]+([[:space:]]+[^-[:space:]][^[:space:]]*)?)*'
explicit="(ANDROID_SERIAL=|$adb_opts[[:space:]]+-s[[:space:]]+)[\"']?([[:alnum:]][^[:space:]\"';|&]*)(.*)"
other_flag="$adb_opts[[:space:]]+-[det]([[:space:]]|\$)"

rest=$cmd
while [[ $rest =~ $explicit ]]; do
  [[ ${BASH_REMATCH[4]} == "$agent" ]] || refuse "${BASH_REMATCH[4]} is not the agents' emulator"
  rest=${BASH_REMATCH[5]}
done

[[ $cmd =~ $other_flag ]] && refuse "adb -d/-e/-t pick a device other than ANDROID_SERIAL"
[[ $cmd =~ (unset|-u)[[:space:]]+ANDROID_SERIAL ]] && refuse "clearing ANDROID_SERIAL lets adb fall back to the user's device"
exit 0
