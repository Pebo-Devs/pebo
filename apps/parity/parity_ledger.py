#!/usr/bin/env python3
"""Ledger helper for the Pebo Apple (iOS + macOS) parity loop.

The ledger (apple-ledger.json) is the source of truth for unit status. This script is
the only thing that reads/writes it, so the loop's bash runner stays simple and the
state machine stays race-free.

Subcommands (LEDGER path via $PARITY_LEDGER, template via $PARITY_TEMPLATE):
  next                         -> print the id of the next ready unit (empty if none)
  ready-count                  -> number of ready units
  summary                      -> human-readable status table
  field <id> <name>            -> print one field of a unit
  set-status <id> <status> [note]
  inc-attempts <id>            -> increment a unit's attempt counter, print new value
  reset <kind>                 -> reset deferred|failed|in_progress units back to pending
  build-prompt <id>            -> render the per-unit prompt from the template

A unit is "ready" when status == pending and every dependency is in {done, deferred}.
"""
import json
import os
import sys
from datetime import datetime, timezone

STATUSES = {"pending", "in_progress", "done", "deferred", "failed"}
UNBLOCKING = {"done", "deferred"}


def ledger_path() -> str:
    p = os.environ.get("PARITY_LEDGER")
    if not p:
        sys.exit("PARITY_LEDGER environment variable is not set")
    return p


def load() -> dict:
    with open(ledger_path(), "r", encoding="utf-8") as fh:
        return json.load(fh)


def save(data: dict) -> None:
    data["updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    path = ledger_path()
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    os.replace(tmp, path)


def units(data: dict) -> list:
    return data.get("units", [])


def by_id(data: dict, uid: str) -> dict:
    for u in units(data):
        if u.get("id") == uid:
            return u
    sys.exit(f"unknown unit id: {uid}")


def status_of(data: dict, uid: str) -> str:
    for u in units(data):
        if u.get("id") == uid:
            return u.get("status", "pending")
    return "pending"


def is_ready(data: dict, u: dict) -> bool:
    if u.get("status") != "pending":
        return False
    for dep in u.get("deps", []):
        if status_of(data, dep) not in UNBLOCKING:
            return False
    return True


def next_ready(data: dict):
    for u in units(data):
        if is_ready(data, u):
            return u
    return None


def cmd_next(data, args):
    u = next_ready(data)
    if u:
        print(u["id"])


def cmd_ready_count(data, args):
    print(sum(1 for u in units(data) if is_ready(data, u)))


def cmd_summary(data, args):
    counts = {}
    for u in units(data):
        counts[u.get("status", "pending")] = counts.get(u.get("status", "pending"), 0) + 1
    total = len(units(data))
    done = counts.get("done", 0)
    print(f"Target : {data.get('target','')}")
    print(f"Branch : {data.get('branch','')}")
    print(f"Updated: {data.get('updated','(never)')}")
    order = ["done", "deferred", "in_progress", "pending", "failed"]
    chips = "  ".join(f"{s}={counts.get(s,0)}" for s in order if counts.get(s, 0))
    print(f"Progress: {done}/{total} done   [{chips}]")
    print("-" * 78)
    icon = {"done": "[x]", "deferred": "[~]", "in_progress": "[>]",
            "pending": "[ ]", "failed": "[!]"}
    for u in units(data):
        st = u.get("status", "pending")
        ready = " READY" if is_ready(data, u) else ""
        att = u.get("attempts", 0)
        att_s = f" (attempt {att})" if att else ""
        print(f"  {icon.get(st,'[ ]')} {u['id']:<32} {st:<11}{att_s}{ready}")
        note = (u.get("result_note") or "").strip()
        if note and st in ("deferred", "failed"):
            print(f"        -> {note[:90]}")


def cmd_field(data, args):
    uid, name = args[0], args[1]
    u = by_id(data, uid)
    val = u.get(name, "")
    if isinstance(val, (list, dict)):
        print(json.dumps(val))
    else:
        print(val)


def cmd_set_status(data, args):
    uid, status = args[0], args[1]
    note = args[2] if len(args) > 2 else ""
    if status not in STATUSES:
        sys.exit(f"invalid status: {status} (allowed: {sorted(STATUSES)})")
    u = by_id(data, uid)
    u["status"] = status
    if note:
        u["result_note"] = note
    u["updated"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    save(data)
    print(f"{uid} -> {status}")


def cmd_inc_attempts(data, args):
    u = by_id(data, args[0])
    u["attempts"] = int(u.get("attempts", 0)) + 1
    save(data)
    print(u["attempts"])


def cmd_reset(data, args):
    kind = args[0] if args else "deferred"
    mapping = {
        "deferred": {"deferred"},
        "failed": {"failed"},
        "in_progress": {"in_progress"},
        "stuck": {"deferred", "failed", "in_progress"},
        "all": {"done", "deferred", "failed", "in_progress"},
    }
    target = mapping.get(kind)
    if not target:
        sys.exit(f"unknown reset kind: {kind} (use {sorted(mapping)})")
    n = 0
    for u in units(data):
        if u.get("status") in target:
            u["status"] = "pending"
            n += 1
    save(data)
    print(f"reset {n} unit(s) from {sorted(target)} to pending")


def cmd_build_prompt(data, args):
    uid = args[0]
    u = by_id(data, uid)
    template_path = os.environ.get("PARITY_TEMPLATE")
    if not template_path or not os.path.exists(template_path):
        sys.exit("PARITY_TEMPLATE is not set or missing")
    with open(template_path, "r", encoding="utf-8") as fh:
        tpl = fh.read()

    dep_lines = []
    for dep in u.get("deps", []):
        dep_lines.append(f"  - {dep}: {status_of(data, dep)}")
    deps_block = "\n".join(dep_lines) if dep_lines else "  (none)"

    summary_lines = []
    for x in units(data):
        summary_lines.append(f"  [{x.get('status','pending'):<11}] {x['id']} — {x['title']}")
    summary_block = "\n".join(summary_lines)

    repl = {
        "{{UNIT_ID}}": u.get("id", ""),
        "{{UNIT_TITLE}}": u.get("title", ""),
        "{{UNIT_CATEGORY}}": u.get("category", ""),
        "{{UNIT_PLATFORMS}}": ", ".join(u.get("platform", [])),
        "{{UNIT_ACCEPTANCE}}": u.get("acceptance", ""),
        "{{UNIT_NOTES}}": u.get("notes", "") or "(none)",
        "{{UNIT_DEPS}}": deps_block,
        "{{LEDGER_TARGET}}": data.get("target", ""),
        "{{LEDGER_SUMMARY}}": summary_block,
        "{{REPO_ROOT}}": os.environ.get("PARITY_REPO_ROOT", "."),
        "{{BRANCH}}": data.get("branch", ""),
    }
    out = tpl
    for k, v in repl.items():
        out = out.replace(k, str(v))
    sys.stdout.write(out)


COMMANDS = {
    "next": cmd_next,
    "ready-count": cmd_ready_count,
    "summary": cmd_summary,
    "field": cmd_field,
    "set-status": cmd_set_status,
    "inc-attempts": cmd_inc_attempts,
    "reset": cmd_reset,
    "build-prompt": cmd_build_prompt,
}


def main(argv):
    if len(argv) < 2 or argv[1] not in COMMANDS:
        sys.exit(f"usage: parity_ledger.py <{'|'.join(COMMANDS)}> [args]")
    data = load()
    COMMANDS[argv[1]](data, argv[2:])


if __name__ == "__main__":
    main(sys.argv)
