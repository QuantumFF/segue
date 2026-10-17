## Commit style

Use Conventional Commits subjects: `type(scope): summary`.

- **type**: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci` or `chore`.
- **scope**: optional, and names the module the change lives in (`library`, `nowplaying`, `playback`, `designsystem`, …). Leave it out when the change spans several modules.
- **summary**: imperative, lowercase first word, no trailing period. Describe what changes for the user, in the words of `GLOSSARY.md`. For example: `feat(library): pinch the grids to resize`, or `fix(playback): keep the queue order after an undo`.

## Agent skills

### Issue tracker

Issues live in GitHub Issues for QuantumFF/segue, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
