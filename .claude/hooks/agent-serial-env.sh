#!/usr/bin/env bash
# SessionStart: points this session's adb and Gradle at its checkout's own agent emulator. If this
# fails, ANDROID_SERIAL keeps the placeholder from .claude/settings.json, which matches no device,
# so adb fails rather than falling back to the user's emulator.
set -euo pipefail

serial=$("$(dirname "$0")/../../scripts/agent-serial" "$CLAUDE_PROJECT_DIR")
if [[ -n ${CLAUDE_ENV_FILE:-} ]]; then
  echo "export ANDROID_SERIAL=$serial" >>"$CLAUDE_ENV_FILE"
fi
