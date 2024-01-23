package dev.remadisson.de.remadisson

import java.time.Instant

data class Ipv4Check(val equals: Boolean){
    val instant: Instant = Instant.now();
}
