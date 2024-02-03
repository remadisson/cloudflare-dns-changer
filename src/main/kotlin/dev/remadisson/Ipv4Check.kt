package dev.remadisson

import java.time.Instant
import java.time.ZonedDateTime

class Ipv4Check(val ipAddress: String?, val id: String?, dateAndTime: String?) {
    var instant: Instant = toInstant(dateAndTime)
    var updated: Boolean = false

    private fun toInstant(dateAndTime: String?): Instant {
        try {
            if (dateAndTime.isNullOrBlank()) return Instant.now()
            val ltd = ZonedDateTime.parse(dateAndTime) ?: return Instant.now()
            return ltd.toInstant()
        } catch (ex: Exception){
            Main.getLogger().error(ex.toString())
            return Instant.now()
        }
    }

    companion object {
        fun empty(): Ipv4Check {
            return Ipv4Check(null, null, null)
        }
    }
}
