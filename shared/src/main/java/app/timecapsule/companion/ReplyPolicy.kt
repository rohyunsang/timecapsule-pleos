package app.timecapsule.companion

object ReplyPolicy {
    const val LOCATION = "LOCATION"
    const val AFTER_THREE_DAYS = "AFTER_THREE_DAYS"
    const val WAIT_MS = 3 * 24 * 60 * 60 * 1000L
    fun waiting(mode: String?, availableAt: Long?, now: Long) = mode == AFTER_THREE_DAYS && (availableAt == null || now < availableAt)
    fun remoteOpen(mode: String?, availableAt: Long?, now: Long) = mode == AFTER_THREE_DAYS && availableAt != null && now >= availableAt
}
