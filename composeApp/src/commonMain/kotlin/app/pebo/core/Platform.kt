package app.pebo.core

/** Platform-provided identity + clock (expect/actual). */
expect fun newId(): String

/** Current time as ISO-8601 UTC string, millisecond precision (e.g. 2026-06-17T21:23:05.123Z). */
expect fun nowIso(): String
