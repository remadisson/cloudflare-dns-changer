package dev.remadisson

import java.awt.Color
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger


val sdf = SimpleDateFormat("dd.MM.yyyy, HH:mm:ss")
class LoggerWrapper(webhookUrl: String?, tag: String?) {

    private val logger: Logger = Logger.getLogger("dev.remadisson.LoggerWrapper")
    private var discordWebhook: DiscordWebhook? = null
    private var tag: String? = null
    init {
        if(!webhookUrl.isNullOrBlank()) {
            this.discordWebhook = DiscordWebhook(webhookUrl)
        } else {
            logger.info("Discord-Webhook not recognized. - Continuing without")
        }

        if(!tag.isNullOrBlank()) {
            this.tag = tag
        } else {
            logger.info("Discord-Tag not found - Continuing without")
        }
    }

    fun info(message: String) {
        logger.info(message)
        toDiscord(LogLevel.INFO, message)
    }

    fun multiLog(context: String, list: List<String>, level: LogLevel) {
        var message = "$context \n"
        for ((i, element) in list.withIndex()) {
            message = message.plus(" $i. $element").plus("\n")
        }
        when(level){
            LogLevel.INFO -> logger.info(message)
            LogLevel.ERROR -> logger.fine("[ERROR] ".plus(message))
            LogLevel.WARNING -> logger.warning(message)
        }

        toDiscord(level, context, *list.toTypedArray())
    }

    fun warning(message: String) {
        logger.warning(message)
        toDiscord(LogLevel.WARNING, message)
    }

    fun error(message: String) {
        logger.fine("[ERROR] $message")
        toDiscord(LogLevel.ERROR, message)
    }

    private fun toDiscord(logLevel: LogLevel, context: String, vararg messages: String) {
        if(discordWebhook == null){
            return
        }

        val webhook: DiscordWebhook = discordWebhook!!

        val timestamp: String = sdf.format(Date())
        webhook.setTts(false)
            .setUsername("rebotisson")
            .setAvatarUrl("https://cdn.discordapp.com/avatars/268362677313601536/ec8322bc8f6a831d43b3bc15d3abb2f7?size=128")

        val embed: DiscordWebhook.EmbedObject = DiscordWebhook.EmbedObject()
        embed.setFooter(
            "This message was sent on $timestamp",
            "https://cdn.discordapp.com/avatars/268362677313601536/ec8322bc8f6a831d43b3bc15d3abb2f7?size=128")
            .setColor(logLevel.color)
            .setTitle(logLevel.name)
            .setDescription(context)
        webhook.addEmbed(embed)

        var ipNull = false

        for(message in messages){
            val check: Ipv4Check = Main.getIpv4Checks()[message] ?: continue
            if(check.updated) {
                embed.addField(message, "IP has been changed to: ${check.ipAddress}", true)
                continue
            }

            if(check.ipAddress != null) {
                    embed.addField(
                        "$message to ${check.ipAddress}",
                        "Last Update: ${sdf.format(Date(check.instant.toEpochMilli()))}",
                        true
                    )
                    continue
            }

            ipNull = true
            break
        }

        if(ipNull){
            embed.setDescription("")
            embed.addField(context, messages.joinToString("\n"), false)
        }

        if (!this.tag.isNullOrBlank() && logLevel != LogLevel.INFO) {
            webhook.setContent("<@$tag>")
        }
        try {
            webhook.execute()
        }catch(ex: IOException){
            println(ex)
            this.error(ex.toString())
        }
    }

    enum class LogLevel(val color: Color?) {
        INFO(Color.GREEN), ERROR(Color.RED), WARNING(Color.YELLOW);
    }
}