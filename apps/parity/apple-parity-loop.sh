#!/usr/bin/env bash
#
# apple-parity-loop.sh — autonomous loop that builds Pebo's iOS + macOS apps to full
# parity with the desktop app, one ledger unit at a time, by driving the GitHub Copilot CLI.
#
# It picks the next ready unit from apple-ledger.json, asks `copilot -p` to implement and
# verify just that unit, records the result (done / deferred / failed), then advances.
# Toolchain-blocked units are recorded as `deferred` so the loop keeps writing code; re-run
# with --retry-deferred in a full Xcode + JDK 21 environment to flip them to `done`.
#
# Usage:
#   ./apple-parity-loop.sh [options]
#
# Options:
#   --max N            Process at most N units this run (default: 999 = until none ready)
#   --once             Process exactly one unit (same as --max 1)
#   --unit ID          Process one specific unit by id (ignores readiness/order)
#   --model NAME       Copilot model to use (default: $PARITY_MODEL, else CLI default)
#   --dry-run          Show the next unit and its prompt; do NOT call Copilot or write state
#   --status           Print the ledger status table and exit
#   --retry-deferred   Reset all `deferred` units to `pending` before running
#   --retry-failed     Reset all `failed` units to `pending` before running
#   --no-commit        Do not git-commit after a successful unit
#   --max-attempts N   Give up on a unit after N failed attempts (default: 3)
#   -h, --help         Show this help
#
# Prerequisites for full completion (loop still runs without them, deferring as needed):
#   * Full Xcode (xcodebuild) + an iOS simulator   * JDK 21   * CocoaPods (if used)
#   * `copilot` on PATH, authenticated
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LEDGER="$SCRIPT_DIR/apple-ledger.json"
TEMPLATE="$SCRIPT_DIR/apple-parity-prompt.md"
LOG_DIR="$SCRIPT_DIR/logs"
RUNLOG="$LOG_DIR/runlog.txt"
HELPER="$SCRIPT_DIR/parity_ledger.py"

PY="$(command -v python3 || command -v python || true)"

export PARITY_LEDGER="$LEDGER"
export PARITY_TEMPLATE="$TEMPLATE"
export PARITY_REPO_ROOT="$REPO_ROOT"

# ---- options ----------------------------------------------------------------
MAX=999
UNIT=""
MODEL="${PARITY_MODEL:-}"
DRY_RUN=0
SHOW_STATUS=0
RETRY_DEFERRED=0
RETRY_FAILED=0
NO_COMMIT=0
MAX_ATTEMPTS=3

die() { echo "error: $*" >&2; exit 1; }
ledger() { "$PY" "$HELPER" "$@"; }

usage() { sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max) MAX="${2:?}"; shift 2;;
    --once) MAX=1; shift;;
    --unit) UNIT="${2:?}"; shift 2;;
    --model) MODEL="${2:?}"; shift 2;;
    --dry-run) DRY_RUN=1; shift;;
    --status) SHOW_STATUS=1; shift;;
    --retry-deferred) RETRY_DEFERRED=1; shift;;
    --retry-failed) RETRY_FAILED=1; shift;;
    --no-commit) NO_COMMIT=1; shift;;
    --max-attempts) MAX_ATTEMPTS="${2:?}"; shift 2;;
    -h|--help) usage; exit 0;;
    *) die "unknown option: $1 (try --help)";;
  esac
done

[[ -n "$PY" ]] || die "python3 (or python) is required"
[[ -f "$LEDGER" ]] || die "ledger not found: $LEDGER"
[[ -f "$TEMPLATE" ]] || die "prompt template not found: $TEMPLATE"
mkdir -p "$LOG_DIR"

if [[ "$SHOW_STATUS" -eq 1 ]]; then
  ledger summary
  exit 0
fi

[[ "$RETRY_DEFERRED" -eq 1 ]] && ledger reset deferred
[[ "$RETRY_FAILED" -eq 1 ]] && ledger reset failed

command -v copilot >/dev/null 2>&1 || {
  [[ "$DRY_RUN" -eq 1 ]] || die "copilot CLI not found on PATH (use --dry-run to preview without it)"
}

# ---- one iteration ----------------------------------------------------------
run_unit() {
  local uid="$1"
  local title; title="$(ledger field "$uid" title)"
  local stamp; stamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  local safe; safe="$(printf '%s' "$uid" | tr -c 'A-Za-z0-9._-' '_')"
  local unit_log="$LOG_DIR/${safe}.log"

  echo ""
  echo "============================================================"
  echo "  UNIT  $uid"
  echo "  $title"
  echo "============================================================"

  if [[ "$DRY_RUN" -eq 1 ]]; then
    echo "--- prompt preview (dry-run, no changes written) ---"
    ledger build-prompt "$uid"
    return 0
  fi

  ledger set-status "$uid" in_progress >/dev/null
  local attempt; attempt="$(ledger inc-attempts "$uid")"
  echo "[$stamp] attempt $attempt — invoking copilot..." | tee -a "$RUNLOG"

  local prompt_file; prompt_file="$(mktemp)"
  ledger build-prompt "$uid" >"$prompt_file"

  local model_args=()
  [[ -n "$MODEL" ]] && model_args=(--model "$MODEL")

  # Drive Copilot non-interactively. Capture the full transcript for debugging.
  # Note: the "${arr[@]+...}" form is required so an empty model_args array does not
  # trip `set -u` on bash 3.2 (the default /bin/bash on macOS).
  set +e
  copilot \
    -p "$(cat "$prompt_file")" \
    --allow-all-tools \
    --no-color \
    -C "$REPO_ROOT" \
    ${model_args[@]+"${model_args[@]}"} \
    2>&1 | tee "$unit_log"
  local rc=${PIPESTATUS[0]}
  set -e
  rm -f "$prompt_file"

  # Parse the agent's last PARITY_RESULT sentinel line.
  local result_line; result_line="$(grep -aE 'PARITY_RESULT:[[:space:]]*(done|deferred|failed)' "$unit_log" | tail -n1 || true)"
  local verdict="" note=""
  if [[ -n "$result_line" ]]; then
    verdict="$(printf '%s' "$result_line" | sed -E 's/.*PARITY_RESULT:[[:space:]]*([a-z]+).*/\1/')"
    note="$(printf '%s' "$result_line" | sed -E 's/.*PARITY_RESULT:[[:space:]]*[a-z]+:?[[:space:]]*//')"
    [[ "$note" == "$result_line" ]] && note=""
  fi

  case "$verdict" in
    done)
      ledger set-status "$uid" done "verified" >/dev/null
      echo "[$(date -u +%FT%TZ)] $uid => DONE" | tee -a "$RUNLOG"
      maybe_commit "$uid" "$title"
      return 0;;
    deferred)
      ledger set-status "$uid" deferred "${note:-blocked by missing toolchain}" >/dev/null
      echo "[$(date -u +%FT%TZ)] $uid => DEFERRED: ${note}" | tee -a "$RUNLOG"
      maybe_commit "$uid" "$title (deferred)"
      return 0;;
    failed)
      finish_failure "$uid" "$attempt" "${note:-agent reported failure}"
      return $?;;
    *)
      finish_failure "$uid" "$attempt" "no PARITY_RESULT sentinel (copilot rc=$rc)"
      return $?;;
  esac
}

finish_failure() {
  local uid="$1" attempt="$2" reason="$3"
  if [[ "$attempt" -ge "$MAX_ATTEMPTS" ]]; then
    ledger set-status "$uid" failed "$reason (after $attempt attempts)" >/dev/null
    echo "[$(date -u +%FT%TZ)] $uid => FAILED: $reason" | tee -a "$RUNLOG"
    return 1
  fi
  ledger set-status "$uid" pending "retry: $reason" >/dev/null
  echo "[$(date -u +%FT%TZ)] $uid => retry queued ($attempt/$MAX_ATTEMPTS): $reason" | tee -a "$RUNLOG"
  return 2
}

maybe_commit() {
  local uid="$1" title="$2"
  [[ "$NO_COMMIT" -eq 1 ]] && return 0
  ( cd "$REPO_ROOT"
    if [[ -n "$(git status --porcelain)" ]]; then
      git add -A
      git commit -q \
        -m "parity($uid): $title" \
        -m "Automated by apps/parity/apple-parity-loop.sh" \
        -m "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>" \
        && echo "  committed changes for $uid"
    else
      echo "  (no file changes to commit for $uid)"
    fi
  )
}

# ---- main loop --------------------------------------------------------------
echo "Pebo Apple parity loop"
echo "  repo   : $REPO_ROOT"
echo "  ledger : $LEDGER"
[[ -n "$MODEL" ]] && echo "  model  : $MODEL" || echo "  model  : (copilot default)"
echo ""

if [[ -n "$UNIT" ]]; then
  run_unit "$UNIT" || true
  echo ""
  ledger summary
  exit 0
fi

processed=0
while [[ "$processed" -lt "$MAX" ]]; do
  next="$(ledger next || true)"
  if [[ -z "$next" ]]; then
    echo ""
    echo "No ready units remain."
    break
  fi
  run_unit "$next"
  status=$?
  processed=$((processed + 1))
  # status 1 = hard failure -> stop so the user can intervene.
  if [[ "$status" -eq 1 ]]; then
    echo ""
    echo "Stopping: unit '$next' failed after $MAX_ATTEMPTS attempts."
    break
  fi
done

echo ""
echo "Processed $processed unit(s) this run."
ledger summary
