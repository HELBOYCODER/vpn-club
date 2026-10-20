package dev.valid7996.narcicgeteway.domain.power

object PowerPolicy {
    data class Cadence(

        val tickMs: Long,

        val notifySeconds: Int,

        val autoPollSeconds: Int,
    )

    fun cadence(screenOn: Boolean): Cadence = if (screenOn) {
        Cadence(tickMs = 1_000L, notifySeconds = 2, autoPollSeconds = 5)
    } else {
        // When screen is off, suppress notification/widget repaints (notifySeconds = -1) to let device sleep
        Cadence(tickMs = 15_000L, notifySeconds = -1, autoPollSeconds = 90)
    }
}
