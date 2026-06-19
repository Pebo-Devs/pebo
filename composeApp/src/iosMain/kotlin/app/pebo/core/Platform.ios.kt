package app.pebo.core

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSISO8601DateFormatWithFractionalSeconds
import platform.Foundation.NSISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSUUID

/** Lower-cased to match the desktop `UUID.randomUUID().toString()` shape. */
actual fun newId(): String = NSUUID().UUIDString().lowercase()

private val iso8601: NSISO8601DateFormatter = NSISO8601DateFormatter().apply {
    formatOptions = NSISO8601DateFormatWithInternetDateTime or NSISO8601DateFormatWithFractionalSeconds
}

/** Millisecond ISO-8601 UTC timestamp, matching the desktop `Instant` format closely enough to round-trip. */
actual fun nowIso(): String = iso8601.stringFromDate(NSDate())
