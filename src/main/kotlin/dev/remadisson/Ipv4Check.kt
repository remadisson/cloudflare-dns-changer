package dev.remadisson

import java.time.Instant

class Ipv4Check (val ipAddress: String?, val id: String?){
    val instant: Instant = Instant.now()
    var updated: Boolean = false
}
