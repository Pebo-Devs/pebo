# Android parity loop

An autonomous loop that ports the Pebo **desktop ("Windows") app** to a native **Android**
app with the **same shared Compose UI** and **all functionality**. It grinds a ledger of
work units to completion, using the GitHub Copilot CLI to implement each unit and a Gradle
build to verify it.

> The UI is already 100% in `composeApp/src/commonMain`. `App.kt` renders a `CompactLayout`
> for narrow screens, so Android reuses the exact same Compose UI — the loop only wires the
> Android target and the platform `actual` implementations.

## Files

| File | Purpose |
| --- | --- |
| `android-ledger.json`     | The unit ledger: id, deps, status, attempts, per-unit `verify` task, and a detailed description used as the agent's instructions. |
| `android-parity-loop.ps1` | The driver: picks the next ready unit, runs the agent, verifies with Gradle, records the result. |
| `unit-prompt.md`          | The per-unit prompt template (tokens are substituted by the driver). |
| `logs/`                   | Per-run agent + verify logs, and UI parity screenshots. |

## How it works

For each **ready** unit (status `pending`, attempts remaining, all `dependsOn` units `done`):

1. Build a focused prompt from `unit-prompt.md` + the unit's description.
2. Run `copilot -p <prompt> --allow-all-tools -C <repo> --model <model>` to implement
   **only that unit**.
3. Run the unit's verify task (default `:composeApp:assembleDebug`).
4. **Verify is the source of truth**: exit 0 → unit `done`; otherwise retry up to
   `maxAttempts`, then mark `failed`.

The loop reloads the ledger every pass, so you can edit it between/under runs. It stops when
no ready units remain, after `-MaxIterations`, or after `-MaxConsecutiveFailures`.

## Prerequisites

- GitHub Copilot CLI on `PATH` (`copilot`).
- JDK 21 (e.g. Android Studio's JBR) and `ANDROID_HOME` pointing at the Android SDK.
- An emulator/AVD for the UI parity unit (e.g. `Pixel_3a_API_34...`).

## Usage

```powershell
cd tools/android-parity

# See the ledger + progress
./android-parity-loop.ps1 -ListOnly

# Preview the next unit + its prompt without touching anything
./android-parity-loop.ps1 -DryRun

# Run the whole loop (default up to 20 iterations)
./android-parity-loop.ps1

# Run a bounded batch
./android-parity-loop.ps1 -MaxIterations 5

# Force a single specific unit (ignores readiness ordering)
./android-parity-loop.ps1 -OnlyUnit android-folder-picker-saf

# Pick the model the agent uses
./android-parity-loop.ps1 -Model claude-opus-4.8
```

### Useful flags

| Flag | Default | Meaning |
| --- | --- | --- |
| `-MaxIterations <n>`          | 20 | Max units to attempt this run. |
| `-MaxConsecutiveFailures <n>` | 3  | Stop after this many verify failures in a row. |
| `-OnlyUnit <id>`              | —  | Run just one unit. |
| `-Model <id>`                 | `claude-opus-4.8` | Model passed to the Copilot CLI. |
| `-SkipVerify`                 | off | Trust the agent run without the Gradle verify (not recommended). |
| `-DryRun`                     | off | Print the next unit + prompt, change nothing. |
| `-ListOnly`                   | off | Print the ledger and exit. |
| `-CopilotExtraArgs @('...')`  | —  | Extra args forwarded to the Copilot CLI. |

## Ledger status values

`pending` → `in_progress` → `done`, or `failed` (terminal, after `maxAttempts`). A unit that
fails a verify but still has attempts left returns to `pending` to be retried on a later pass.

To re-run a unit, set its `status` back to `pending` (and optionally `attempts` to `0`) in
`android-ledger.json`.

## Editing the plan

Add or refine units directly in `android-ledger.json`. Each unit's `description` is the
authoritative instruction the agent receives, so make it specific. Keep `dependsOn` accurate
so the loop sequences foundational work before features.
