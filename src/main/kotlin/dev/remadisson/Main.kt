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

val ipv4Checks: HashMap<String, Ipv4Check> = HashMap()
private const val updateInterval: Long = 30 // Minutes

fun main() {
    val webhook = environmentHelper(EnvironmentKey.WEBHOOK)
    val discordTag = environmentHelper(EnvironmentKey.TAG)

    val logger = LoggerWrapper(webhook, discordTag)

    val cfEmail = environmentHelper(EnvironmentKey.EMAIL)
    val cfAuthToken = environmentHelper(EnvironmentKey.TOKEN)

    if (cfEmail.isNullOrBlank() || cfAuthToken.isNullOrBlank()) {
        logger.error("Cloudflare Email or AuthToken not provided")
        return;
    }

    val zoneID: String? = environmentHelper(EnvironmentKey.ZONE)
    if (zoneID.isNullOrBlank()) {
        logger.error("zoneID was not provided.")
        return;
    }

    val main = Main()
    main.zoneID = zoneID
    main.logger = logger
    main.email = cfEmail
    main.token = cfAuthToken
    val subDomains: List<String> = environmentHelper(EnvironmentKey.SUBDOMAINS)?.split(",")?.toList()?.map(String::trim)?.distinct() ?: emptyList()
    if (subDomains.isEmpty()) {
        logger.error("No subdomains have been provided.")
        return;
    }

    for (subDomain in subDomains) {
        ipv4Checks[subDomain] = Ipv4Check(null, null);
    }

    val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    logger.info("Started: Updating ${ipv4Checks.size} DNS-Records in an Interval of $updateInterval Minutes, Following Subdomains have been detected", subDomains)
    scheduler.scheduleAtFixedRate(main::scheduledUpdate, 0, updateInterval, TimeUnit.MINUTES)
}

private fun environmentHelper(key: EnvironmentKey) : String?{
    if(!System.getenv().contains(key.environmentPath)){
        return null
    }

    val value = System.getenv()[key.environmentPath]
    if(value.equals(key.defaultValue())){
        return null
    }

    return value
}

private enum class EnvironmentKey(val environmentPath: String) {
    WEBHOOK("DC_WEBHOOK"), EMAIL("CF_EMAIL"), TOKEN("CF_TOKEN"),
    ZONE("CF_ZONE"), SUBDOMAINS("CF_SUBDOMAINS"), TAG("DC_TAG");

    fun defaultValue(): String {
        return "default"
    }
}

class Main {

    private var currentIpAddress: String? = null;
    private var lastUpdate: Instant? = null;
    private var init: Boolean = true;
    lateinit var token: String;
    lateinit var email: String;
    lateinit var zoneID: String;
    lateinit var logger: LoggerWrapper;

    fun scheduledUpdate() {
        val queriedIpAddress: String = getCurrentIpAddress()
        if (currentIpAddress == null || !Objects.equals(currentIpAddress, queriedIpAddress)) {
            if(currentIpAddress != null){
                logger.info("$queriedIpAddress is the new IP-Address.")
            }
            currentIpAddress = queriedIpAddress;
            lastUpdate = Instant.now();
        } else {
            return;
        }

        if (init) {
            val idEndpoint = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records"
            val response: JsonObject = cloudflareGetRequest(idEndpoint) ?: return

            val jsonArray: JsonArray = response.get("result").asJsonArray
            for (iterator in jsonArray) {
                val element: JsonObject = iterator.asJsonObject ?: continue

                if (!element.has("id") || !element.has("name") || !element.has("id") || !element.has("type")) {
                    continue
                }

                val name: String = element.get("name").asString
                val id: String = element.get("id").asString
                val content: String = element.get("content").asString
                val type: String = element.get("type").asString
                if (ipv4Checks.keys.contains(name)) {
                    if(type != "A"){
                        //TODO Needs to be tested, could throw an error because of the newline character
                        logger.warning("Named Subdomain '$name' does not match the type that is required to update the entry.\nRequired Type: A given '$type'")
                        ipv4Checks.remove(name)
                        continue;
                    }
                    ipv4Checks[name] = Ipv4Check(content, id)
                }
            }

            ipv4Checks.entries.stream().filter{(_, v): Map.Entry<String, Ipv4Check> -> v.id == null }.map { ipv4Checks.remove(it.key) }
        }

        val entries: ArrayList<String> = ArrayList()
        for (entry in ipv4Checks.entries) {
            if (Objects.equals(entry.value.ipAddress, currentIpAddress)) {
                entries.add(entry.key)
                continue
            }

            val body = JsonObject();
            body.addProperty("type", "A")
            body.addProperty("name", entry.key)
            body.addProperty("content", currentIpAddress)
            body.addProperty("ttl", 1)
            body.addProperty("proxied", false).toString()

            val url = "https://api.cloudflare.com/client/v4/zones/$zoneID/dns_records/${entry.value.id}"
            val json = cloudflarePutRequest(url, body.toString()) ?: continue

            if(json.has("error")){
                logger.error("${entry.key} has been found with an error ${json.toString()}")
            } else {
                ipv4Checks[entry.key] = Ipv4Check(currentIpAddress, entry.value.id)
                if(ipv4Checks.contains(entry.key)) {
                    ipv4Checks[entry.key]?.updated = true
                }
                entries.add(entry.key)
            }

            Thread.sleep(200)
        }

        if(entries.isNotEmpty() && !init) {
            logger.info("Update about following Subdomains", entries)
        }

        if(init) init = false
    }

    private fun getCurrentIpAddress(): String {
        val url: URL = URI.create("https://checkip.amazonaws.com").toURL()
        val reader = BufferedReader(InputStreamReader(url.openStream()));
        val ipAddress: String = reader.readLine();
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