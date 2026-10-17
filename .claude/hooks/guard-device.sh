#!/usr/bin/env bash
# Keeps each agent on its own emulator. The user runs the app on segue_pixel (and maybe a phone),
# and agents in other worktrees run theirs; this session's emulator is the one scripts/agent-serial
# gives its checkout. The SessionStart hook already points adb and Gradle at it, so this only
# refuses a Bash command that reaches for some other device.
set -euo pipefail

cmd=$(jq -r '.tool_input.command // empty')
case $cmd in *adb* | *emulator* | *gradlew* | *ANDROID_SERIAL*) ;; *) exit 0 ;; esac

agent=$("$(dirname "$0")/../../scripts/agent-serial" "$CLAUDE_PROJECT_DIR" 2>/dev/null) || agent=none

refuse() {
  echo "Blocked: $1. Other devices belong to the user or to agents in other worktrees. Use" \
    "this checkout's emulator ($agent): scripts/agent-emulator boots it, and adb and Gradle" \
    "already target it. See .claude/skills/emulator/SKILL.md." >&2
  exit 2
}

[[ $cmd == *segue_pixel* ]] && refuse "segue_pixel is the user's emulator"

rest=$cmd
while [[ $rest =~ (emulator-[0-9]+)(.*) ]]; do
  [[ ${BASH_REMATCH[1]} == "$agent" ]] || refuse "${BASH_REMATCH[1]} is not this checkout's emulator"
  rest=${BASH_REMATCH[2]}
done

# Regexes live in variables: ; & | inside [[ =~ ]] would end the expression. adb_opts is adb plus
# its own options, so a flag after the subcommand (`adb shell ls -t`) does not count.
adb_opts='adb([[:space:]]+-[[:alpha:]]+([[:space:]]+[^-[:space:]][^[:space:]]*)?)*'
explicit="(ANDROID_SERIAL=|$adb_opts[[:space:]]+-s[[:space:]]+)[\"']?([[:alnum:]][^[:space:]\"';|&]*)(.*)"
other_flag="$adb_opts[[:space:]]+-[det]([[:space:]]|\$)"

rest=$cmd
while [[ $rest =~ $explicit ]]; do
  [[ ${BASH_REMATCH[4]} == "$agent" ]] || refuse "${BASH_REMATCH[4]} is not this checkout's emulator"
  rest=${BASH_REMATCH[5]}
done

[[ $cmd =~ $other_flag ]] && refuse "adb -d/-e/-t pick a device other than ANDROID_SERIAL"
[[ $cmd =~ (unset|-u)[[:space:]]+ANDROID_SERIAL ]] && refuse "clearing ANDROID_SERIAL lets adb fall back to the user's device"
exit 0
