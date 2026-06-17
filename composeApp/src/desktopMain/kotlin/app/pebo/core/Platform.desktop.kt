package app.pebo.core

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

actual fun newId(): String = UUID.randomUUID().toString()

actual fun nowIso(): String = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString()
