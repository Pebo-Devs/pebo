package app.pebo.core

/** Formats an ISO-8601 timestamp into a short, human label like "Jun 17" or "Jun 17, 2025". */
object DateLabel {
    private val months = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    fun short(iso: String): String {
        if (iso.length < 10) return iso
        val year = iso.substring(0, 4)
        val month = iso.substring(5, 7).toIntOrNull() ?: return iso
        val day = iso.substring(8, 10).trimStart('0').ifEmpty { "0" }
        val mon = months.getOrElse(month - 1) { return iso }
        val currentYear = nowIso().substring(0, 4)
        return if (year == currentYear) "$mon $day" else "$mon $day, $year"
    }
}
