package dev.remadisson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.system.exitProcess

private val missing = ArrayList<String>()
fun main() {
    val webhook = informationProvider(InformationKey.WEBHOOK)
    val discordTag = informationProvider(InformationKey.TAG)

    val logger = LoggerWrapper(webhook, discordTag)
    val cfEmail: String? = informationProvider(InformationKey.EMAIL)
    val cfAuthToken: String? = informationProvider(InformationKey.TOKEN)
    val zoneID: String? = informationProvider(InformationKey.ZONE)
    val subDomains: List<String> =
        informationProvider(InformationKey.SUBDOMAINS)?.split(",")?.toList()?.map(String::trim)?.distinct()
            ?: emptyList()

    if (missing.isNotEmpty()) {
        logger.multiLog("Current Args have not been provided but are necessary", missing, LoggerWrapper.LogLevel.ERROR)
        logger.raw("Program shutdown in 10 Seconds.")
        Thread.sleep(10000)
        exitProcess(-1)
    }

    val main = Main.setMain(Main(logger, zoneID!!, cfEmail!!, cfAuthToken!!))
    val ipv4Checks = Main.getIpv4Checks()
    val updateInterval = Main.getUpdateInterval()

    for (subDomain in subDomains) {
        ipv4Checks[subDomain] = Ipv4Check.empty()
    }

    val timestamp1 = System.currentTimeMillis();
    val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    scheduler.scheduleAtFixedRate(main::scheduledUpdate, 0, updateInterval, TimeUnit.MINUTES)
    while (main.init) {
        Thread.sleep(50)
    }
    val timestamp2 = System.currentTimeMillis();
    val timeDifference = (timestamp2 - timestamp1) / 1000.0
    logger.multiMapLog(
        "Started: Fetch took about $timeDifference Seconds! Updating ${ipv4Checks.size} DNS-Record" + (if (ipv4Checks.size > 1) "s" else "") + " in an Interval of $updateInterval Minutes and the following Subdomains have been detected",
        "lastly updated at:",
        ipv4Checks.mapValues { (_, value) -> value.getTimeStamp() },
        LoggerWrapper.LogLevel.INFO)
}

private fun informationProvider(key: InformationKey): String? {
    if ((System.getenv()[key.path] ?: key.defaultValue()) != key.defaultValue()) {
        val value: String? = System.getenv()[key.path]
        if (value.isNullOrBlank()) {
            missing.add(key.path)
        }
        return value
    }

    if (System.getProperty(key.path, key.defaultValue()) != key.defaultValue()) {
        val value: String? = System.getProperty(key.path)
        if (value.isNullOrBlank()) {
            missing.add(key.path)
        }
        return value
    }

    missing.add(key.path)
    return null
}

private enum class InformationKey(val path: String) {
    WEBHOOK("DC_WEBHOOK"), EMAIL("CF_EMAIL"), TOKEN("CF_TOKEN"),
    ZONE("CF_ZONE"), SUBDOMAINS("CF_SUBDOMAINS"), TAG("DC_TAG");

    fun defaultValue(): String {
        return "default"
    }
}

class Main(
    val logger: LoggerWrapper,
    private val zoneID: String,
    private val email: String,
    private val token: String
) {

    private var currentIpAddress: String? = null
    private var lastUpdate: Instant? = null
    @Volatile
    var init: Boolean = true
        private set

    companion object {
        private lateinit var main: Main
        private val ipv4Checks: HashMap<String, Ipv4Check> = HashMap()
        private const val UPDATE_INTERVAL: Long = 30

        fun setMain(main: Main): Main {
            this.main = main
            return main
        }

        fun getIpv4Checks(): HashMap<String, Ipv4Check> {
            return ipv4Checks
        }

        fun getUpdateInterval(): Long {
            return UPDATE_INTERVAL
        }

        fun getLogger(): LoggerWrapper {
            return main.logger
        }
    }

    fun scheduledUpdate() {
        val queriedIpAddress: String = getCurrentIpAddress()
        if (currentIpAddress == null || !Objects.equals(currentIpAddress, queriedIpAddress)) {
            if (currentIpAddress != null) {
                logger.info("$queriedIpAddress is the new IP-Address.")
            }
            currentIpAddress = queriedIpAddress
            lastUpdate = Instant.now()
        } else {
            return
        }

        if (this.init) {
            val idEndpoint = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records"
            val response: JsonObject = cloudflareGetRequest(idEndpoint) ?: return

            val jsonArray: JsonArray = response.get("result").asJsonArray
            for (iterator in jsonArray) {
                val element: JsonObject = iterator.asJsonObject ?: continue

                if (!element.has("id") || !element.has("name") || !element.has("content") || !element.has("type") || !element.has(
                        "modified_on"
                    )
                ) {
                    println("Skipped entry because of missing attribute")
                    continue
                }

                val name: String = element.get("name").asString
                val id: String = element.get("id").asString
                val content: String = element.get("content").asString
                val type: String = element.get("type").asString
                val lastModified: String = element.get("modified_on").asString

                if (ipv4Checks.keys.contains(name)) {
                    if (type != "A") {
                        logger.warning("Named Subdomain '$name' does not match the type that is required to update the entry. Required Type: A - Given: '$type' - Entry removed from update List.")
                        ipv4Checks.remove(name)
                        continue
                    }

                    ipv4Checks[name] = Ipv4Check(content, id, lastModified)
                }
                Thread.sleep(50)
            }

            val notFound = ArrayList<String>()
            ipv4Checks.entries.removeIf { (k, v): Map.Entry<String, Ipv4Check> ->
                val isNotFound = (v.id == null)
                if (isNotFound) {
                    notFound.add(k)
                }
                isNotFound
            }

            if (notFound.isNotEmpty()) {
                logger.multiLog(
                    "Following Subdomains could not be found at Cloudflare",
                    notFound,
                    LoggerWrapper.LogLevel.INFO
                )
            }
        }

        val entries: ArrayList<String> = ArrayList()
        for ((key, value) in ipv4Checks.entries) {
            if (Objects.equals(value.ipAddress, currentIpAddress)) {
                entries.add(key)
                continue
            }

            val body = JsonObject()
            body.addProperty("type", "A")
            body.addProperty("name", key)
            body.addProperty("content", currentIpAddress)
            body.addProperty("ttl", 1)
            body.addProperty("proxied", false).toString()

            val url = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records/${value.id}"
            val json = cloudflarePutRequest(url, body.toString()) ?: continue
            if (json.has("errors") && json.get("errors").isJsonArray && json.get("errors").asJsonArray.size() > 0) {
                logger.error("$key has been found with an error $json")
                continue
            }

            val lastModified: String = json.get("modified_on").asString ?: Instant.now().toString()
            ipv4Checks[key] = Ipv4Check(currentIpAddress, value.id, lastModified)
            if (ipv4Checks.contains(key)) {
                ipv4Checks[key]?.updated = true
            }
            entries.add(key)
            Thread.sleep(200)
        }

        if (entries.isNotEmpty() && !this.init) {
            logger.multiLog("Update about following Subdomains", entries, LoggerWrapper.LogLevel.INFO)
        }

        if (this.init) { this.init = false }
    }

    private fun getCurrentIpAddress(): String {
        val url: URL = URI.create("https://checkip.amazonaws.com").toURL()
        val reader = BufferedReader(InputStreamReader(url.openStream()))
        val ipAddress: String = reader.readLine()
        reader.close()
        return ipAddress
    }

    private fun cloudflareGetRequest(urlString: String): JsonObject? {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(urlString))
            .header("X-Auth-Email", email)
            .header("X-Auth-Key", token)
            .header("Content-Type", "application/json")
            .GET()
            .build()
        val client: HttpClient = HttpClient.newHttpClient()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return JsonParser().parse(response.body()).asJsonObject
    }

    private fun cloudflarePutRequest(urlString: String, body: String): JsonObject? {
        val request: HttpRequest = HttpRequest.newBuilder()
            .uri(URI.create(urlString))
            .header("X-Auth-Email", email)
            .header("X-Auth-Key", token)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val client: HttpClient = HttpClient.newHttpClient()
        val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

        return JsonParser().parse(response.body()).asJsonObject
    }
}