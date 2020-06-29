package com.kenvix.complexbot

import com.kenvix.moecraftbot.ng.Defines
import com.kenvix.moecraftbot.ng.lib.exception.BusinessLogicException
import com.kenvix.moecraftbot.ng.lib.exception.UserInvalidUsageException
import com.kenvix.moecraftbot.ng.lib.exception.UserViolationException
import com.kenvix.moecraftbot.ng.lib.nameAndHashcode
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.MessagePacketSubscribersBuilder
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.content
import org.slf4j.LoggerFactory
import java.lang.NumberFormatException

val commandPrefix: String = Defines.systemOptions.bot.commandPrefix
val commandPrefixLength = commandPrefix.length
val enabledFeatures = ArrayList<BotFeature>()
lateinit var commands: HashMap<String, RegisteredBotCommand>
val logger = LoggerFactory.getLogger("ComplexBot.ExtensionUtils")
lateinit var callBridge: CallBridge

fun MessagePacketSubscribersBuilder.command(command: String,
                                            handler: BotCommandFeature,
                                            vararg middleware: BotMiddleware
) {
    if (!::commands.isInitialized) {
        commands = HashMap()
        this.startsWith(
                prefix = commandPrefix,
                trim = true,
                onEvent = {
                    executeCatchingBusinessException {
                        val requestedCommand = this.message.content.run {
                            val spacePos = this.indexOf(' ')
                            if (spacePos == -1)
                                substring(commandPrefixLength)
                            else
                                substring(commandPrefixLength, spacePos)
                        }
                        commands[requestedCommand]?.also {
                            var success = true
                            if (it.middlewares != null) {
                                for (middle in it.middlewares) {
                                    success = success && middle.onMessage(this@startsWith)
                                    if (!success) break
                                }
                            }

                            if (success)
                                it.handler.onMessage(this@startsWith)
                        }
                    }
                }
        )
    }

    commands[command] = RegisteredBotCommand(handler, middleware)
}

interface BotCommandFeature {
    @Throws(UserInvalidUsageException::class, NumberFormatException::class)
    suspend fun onMessage(msg: MessageEvent)
}

interface BotFeature {
    fun onEnable(bot: Bot)
}

fun Bot.addFeature(handler: BotFeature) {
    enabledFeatures.add(handler)
    handler.onEnable(this)
}

suspend fun MessageEvent.executeCatchingBusinessException(function: suspend (() -> Unit)): Boolean {
    return try {
        function()
        true
    } catch (exception: Throwable) {
        when (exception) {
            is UserViolationException, is NumberFormatException -> {
                sendExceptionMessage(exception)
            }

            is BusinessLogicException -> {
                sendExceptionMessage(exception)
                logger.warn("An unhandled business exception is thrown ", exception)
            }

            is NotImplementedError -> {
                logger.warn("An Unimplemented method is called. ", exception)
            }

            else -> throw exception
        }

        false
    }
}

suspend fun MessageEvent.sendExceptionMessage(exception: Throwable) {
    this.reply(kotlin.run {
        if (exception.localizedMessage.isNullOrBlank())
            "操作失败：${exception.nameAndHashcode}"
        else
            exception.localizedMessage
    })
}

interface BotMiddleware {
    suspend fun onMessage(msg: MessageEvent): Boolean
}

data class RegisteredBotCommand(
        val handler: BotCommandFeature,
        val middlewares: Array<out BotMiddleware>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegisteredBotCommand

        if (handler != other.handler) return false
        if (middlewares != null) {
            if (other.middlewares == null) return false
            if (!middlewares.contentEquals(other.middlewares)) return false
        } else if (other.middlewares != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = handler.hashCode()
        result = 31 * result + (middlewares?.contentHashCode() ?: 0)
        return result
    }
}

fun isBotSystemAdministrator(qq: Long): Boolean {
    return callBridge.config.bot.administratorIds != null && qq in callBridge.config.bot.administratorIds
}