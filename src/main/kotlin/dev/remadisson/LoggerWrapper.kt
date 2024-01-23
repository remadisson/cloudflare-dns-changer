package de.remadisson

import java.util.logging.Logger

object LoggerWrapper {
    val logger: Logger = Logger.getLogger("dns-changer")
    val hook: DiscordWebhook = DiscordWebhook("https://discord.com/api/webhooks/1198662985237934111/jhKA1w6K0Xi4CM6e2N3xhmojZTfeX3Bh2jUYtSSyZwiRZaN5DY_DBVmV4yCc6nzFATvG")

    fun info(message: String){

    }

    fun warning(message: String){

    }

    fun error(message: String){

    }
}