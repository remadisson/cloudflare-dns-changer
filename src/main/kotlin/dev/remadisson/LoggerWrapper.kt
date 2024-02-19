package dev.remadisson

import java.awt.Color
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger
import kotlin.reflect.KMutableProperty1


val sdf = SimpleDateFormat("dd.MM.yyyy, HH:mm:ss")

class LoggerWrapper(webhookUrl: String?, tag: String?) {

    private val logger: Logger = Logger.getLogger("dev.remadisson.LoggerWrapper")
    private var discordWebhook: DiscordWebhook? = null
    private var tag: String? = null

    init {
        if (!webhookUrl.isNullOrBlank()) {
            this.discordWebhook = DiscordWebhook(webhookUrl)
        } else {
            warning("Discord-Webhook not recognized. - Continuing without")
        }

        if (!tag.isNullOrBlank()) {
            this.tag = tag
        } else {
            warning("Discord-Tag not found - Continuing without")
        }
    }

    fun raw(message: String){
        logger.info(message)
    }
    fun info(message: String) {
        logger.info(message)
        toDiscord<String>(LogLevel.INFO, message)
    }

    fun multiLog(context: String, list: List<String>, level: LogLevel) {
        var message = "$context \n"
        for ((i, element) in list.withIndex()) {
            message = message.plus(" $i. $element").plus("\n")
        }
        when (level) {
            LogLevel.INFO -> logger.info(message)
            LogLevel.ERROR -> logger.warning("[ERROR] $message")
            LogLevel.WARNING -> logger.warning(message)
        }
        toDiscord(level, context, *list.toTypedArray())
    }

    fun <T> multiMapLog(context: String, connector: String, map: Map<String, T>, logLevel: LogLevel) {
        val list: List<String> = map.map { (k, v) -> "$k $connector ${v.toString()}" }.toList()
        multiLog(context, list, logLevel)
    }

    fun warning(message: String) {
        logger.warning(message)
        toDiscord<String>(LogLevel.WARNING, message)
    }

    fun error(message: String) {
        logger.warning("[ERROR] $message")
        toDiscord<String>(LogLevel.ERROR, message)
    }

    private fun <T> toDiscord(logLevel: LogLevel, context: String, vararg messages: T) {
        if (discordWebhook == null) {
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
            "https://cdn.discordapp.com/avatars/268362677313601536/ec8322bc8f6a831d43b3bc15d3abb2f7?size=128"
        )
            .setColor(logLevel.color)
            .setTitle(logLevel.name)
            .setDescription(context)
        webhook.addEmbed(embed)

        if(messages.isNotEmpty()) {
            if (messages.firstOrNull() is DiscordWebhook.EmbedObject.Field) {
                for (message in messages) {
                    val field = message as DiscordWebhook.EmbedObject.Field
                    embed.addField(field)
                }
            } else {
                distributeItemsToFields(messages, embed)
            }
        }

        if (!this.tag.isNullOrBlank() && logLevel.tagged) {
            webhook.setContent("<@$tag>")
        }

        try {
            webhook.execute()
        } catch (ex: IOException) {
            this.error(ex.toString())
        }
    }

    private fun <T> distributeItemsToFields(messages: Array<T>, embed: DiscordWebhook.EmbedObject) {
        var embedCounter = 0
        var embedString = ""
        val embedTooMuchInput = "There are too many items. %d Items could not be logged."
        for ((i, message) in messages.withIndex()) {

            if (embed.getFields().size == 25 && (message.toString().length + embedString.length) < (1024 - (embedTooMuchInput.length + 5))) {
                val leftItems = (messages.size - (i + 1))
                embedString += " " + String.format(embedTooMuchInput, leftItems)
                break;
            }

            if (message.toString().length < 1024) {
                if (embedString.isBlank()) {
                    embedString += message.toString()
                } else {
                    embedString += ", ${message.toString()}"
                }
                embedCounter++
            }

            if (i + 1 <= messages.size - 1 && (messages[i + 1].toString().length + embedString.length) >= 1024 || i == messages.size - 1) {
                if (embed.getFields().size <= 25) {
                    embed.addField("Containing $embedCounter items", embedString, false)
                    embedString = ""
                    embedCounter = 0
                }
            }
        }

    }

    enum class LogLevel(val color: Color?, val tagged: Boolean) {
        INFO(Color.GREEN, false), ERROR(Color.RED, true), WARNING(Color.YELLOW, true);
    }
}