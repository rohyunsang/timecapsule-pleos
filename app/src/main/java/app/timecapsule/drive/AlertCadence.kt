package app.timecapsule.drive

/** Monotonic time keeps phone/car clock changes from causing bursts of prompts. */
object AlertCadence {
    val choicesMinutes = listOf(1, 5, 10)
    fun allows(quiet: Boolean, intervalMinutes: Int, lastAlertAt: Long?, now: Long): Boolean {
        if (quiet) return false
        val interval = intervalMinutes.takeIf { it in choicesMinutes } ?: 5
        return lastAlertAt == null || (now >= lastAlertAt && now - lastAlertAt >= interval * 60_000L)
    }
}
