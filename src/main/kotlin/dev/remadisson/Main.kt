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

fun main() {
    val webhook = informationProvider(InformationKey.WEBHOOK)
    val discordTag = informationProvider(InformationKey.TAG)

    val logger = LoggerWrapper(webhook, discordTag)

    val missing = ArrayList<String>()

    val cfEmail: String? = informationProvider(InformationKey.EMAIL)
    if (cfEmail.isNullOrBlank()) {
        missing.add("CF_EMAIL was not provided")
    }

    val cfAuthToken: String? = informationProvider(InformationKey.TOKEN)
    if(cfAuthToken.isNullOrBlank()){
        missing.add("CF_TOKEN was not provided")
    }

    val zoneID: String? = informationProvider(InformationKey.ZONE)
    if (zoneID.isNullOrBlank()) {
        missing.add("CF_ZONE was not provided")
    }

    val subDomains: List<String> = informationProvider(InformationKey.SUBDOMAINS)?.split(",")?.toList()?.map(String::trim)?.distinct() ?: emptyList()
    if(subDomains.isEmpty()){
        missing.add("CF_SUBDOMAINS is either in wrong format, or just not provided")
    }

    if(missing.isNotEmpty()){
        logger.multiLog("Current Args have not been provided but are necessary", missing, LoggerWrapper.LogLevel.ERROR)
        exitProcess(-1)
    }

    val main = Main.setMain(Main(logger, zoneID!!, cfEmail!!, cfAuthToken!!))
    val ipv4Checks = Main.getIpv4Checks()
    val updateInterval = Main.getUpdateInterval()

    for (subDomain in subDomains) {
        ipv4Checks[subDomain] = Ipv4Check.empty()
    }

    val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    logger.multiLog("Started: Updating ${ipv4Checks.size} DNS-Records in an Interval of $updateInterval Minutes, Following Subdomains have been detected", subDomains, LoggerWrapper.LogLevel.INFO)
    scheduler.scheduleAtFixedRate(main::scheduledUpdate, 0, updateInterval, TimeUnit.MINUTES)
}

private fun informationProvider(key: InformationKey) : String?{
    if((System.getenv()[key.path] ?: key.defaultValue()) != key.defaultValue()){
        return System.getenv()[key.path]
    }

    if(System.getProperty(key.path, key.defaultValue()) != key.defaultValue()){
        return System.getProperty(key.path)
    }

    return null
}

private enum class InformationKey(val path: String) {
    WEBHOOK("DC_WEBHOOK"), EMAIL("CF_EMAIL"), TOKEN("CF_TOKEN"),
    ZONE("CF_ZONE"), SUBDOMAINS("CF_SUBDOMAINS"), TAG("DC_TAG");

    fun defaultValue(): String {
        return "default"
    }
}

class Main(val logger: LoggerWrapper, private val zoneID: String, private val email: String, private val token: String) {

    private var currentIpAddress: String? = null
    private var lastUpdate: Instant? = null
    private var init: Boolean = true

    companion object {
        private lateinit var main: Main
        private val ipv4Checks: HashMap<String, Ipv4Check> = HashMap()
        private const val UPDATE_INTERVAL: Long = 30

        fun setMain(main: Main): Main{
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
            if(currentIpAddress != null){
                logger.info("$queriedIpAddress is the new IP-Address.")
            }
            currentIpAddress = queriedIpAddress
            lastUpdate = Instant.now()
        } else {
            return
        }

        if (init) {
            val idEndpoint = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records"
            val response: JsonObject = cloudflareGetRequest(idEndpoint) ?: return

            val jsonArray: JsonArray = response.get("result").asJsonArray
            for (iterator in jsonArray) {
                val element: JsonObject = iterator.asJsonObject ?: continue

                if (!element.has("id") || !element.has("name") || !element.has("content") || !element.has("type") || !element.has("modified_on")) {
                    println("Skipped entry because of missing attribute")
                    continue
                }

                val name: String = element.get("name").asString
                val id: String = element.get("id").asString
                val content: String = element.get("content").asString
                val type: String = element.get("type").asString
                val lastModified: String = element.get("modified_on").asString

                if (ipv4Checks.keys.contains(name)) {
                    if(type != "A"){
                        logger.warning("Named Subdomain '$name' does not match the type that is required to update the entry. Required Type: A - Given: '$type' - Entry removed from update List.")
                        ipv4Checks.remove(name)
                        continue
                    }

                    ipv4Checks[name] = Ipv4Check(content, id, lastModified)
                }
                Thread.sleep(50)
            }

            val notFound = ArrayList<String>()
            ipv4Checks.entries.removeIf{ (k, v): Map.Entry<String, Ipv4Check> ->
                val isNotFound = (v.id == null)
                if(isNotFound) {
                    notFound.add(k)
                }
                isNotFound
            }

            if(notFound.isNotEmpty()){
                logger.multiLog("Following Subdomains could not be found at Cloudflare", notFound, LoggerWrapper.LogLevel.INFO)
            }
        }

        val entries: ArrayList<String> = ArrayList()
        for (entry in ipv4Checks.entries) {
            if (Objects.equals(entry.value.ipAddress, currentIpAddress)) {
                entries.add(entry.key)
                continue
            }

            val body = JsonObject()
            body.addProperty("type", "A")
            body.addProperty("name", entry.key)
            body.addProperty("content", currentIpAddress)
            body.addProperty("ttl", 1)
            body.addProperty("proxied", false).toString()

            val url = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records/${entry.value.id}"
            val json = cloudflarePutRequest(url, body.toString()) ?: continue
            if(json.has("error")){
                logger.error("${entry.key} has been found with an error $json")
            } else {
                val lastModified: String = json.get("modified_on").asString ?: Instant.now().toString()
                ipv4Checks[entry.key] = Ipv4Check(currentIpAddress, entry.value.id, lastModified)
                if(ipv4Checks.contains(entry.key)) {
                    ipv4Checks[entry.key]?.updated = true
                }
                entries.add(entry.key)
            }
            Thread.sleep(200)
        }

        if(entries.isNotEmpty() && !init) {
            logger.multiLog("Update about following Subdomains", entries, LoggerWrapper.LogLevel.INFO)
        }

        if(init) init = false
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