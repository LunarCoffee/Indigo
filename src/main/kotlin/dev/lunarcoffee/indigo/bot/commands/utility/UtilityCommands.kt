package dev.lunarcoffee.indigo.bot.commands.utility

import dev.lunarcoffee.indigo.bot.commands.utility.help.CommandHelpSender
import dev.lunarcoffee.indigo.bot.commands.utility.help.ListHelpSender
import dev.lunarcoffee.indigo.bot.commands.utility.remind.Reminder
import dev.lunarcoffee.indigo.bot.commands.utility.remind.ReminderManager
import dev.lunarcoffee.indigo.bot.util.formatTimeOnly
import dev.lunarcoffee.indigo.bot.util.success
import dev.lunarcoffee.indigo.bot.util.toZoned
import dev.lunarcoffee.indigo.bot.util.zones.ZoneManager
import dev.lunarcoffee.indigo.framework.api.dsl.command
import dev.lunarcoffee.indigo.framework.api.exts.remove
import dev.lunarcoffee.indigo.framework.api.exts.send
import dev.lunarcoffee.indigo.framework.core.bot.CommandBot
import dev.lunarcoffee.indigo.framework.core.commands.CommandGroup
import dev.lunarcoffee.indigo.framework.core.commands.transformers.*
import java.time.ZoneId
import java.time.ZonedDateTime

@CommandGroup("Utility")
class UtilityCommands {
    fun remindIn() = command("remindin") {
        description = """
            |`$name <delay> [message]`
            |Sets a reminder to fire off and ping in some amount of time.
        """.trimMargin()

        execute(TrTime, TrRestJoined.optional("(no message)")) { (delay, message) ->
            check(message, "Your message can be at most 500 characters!") { length > 500 } ?: return@execute

            val timeAfter = delay.asTimeFromNow(ZoneId.systemDefault())
            val reminder = event.run { Reminder(message, timeAfter, guild.id, channel.id, messageId, author.id) }

            ReminderManager.addReminder(reminder, jda)
            success("I will remind you in `$delay`!")
        }
    }

    fun remindAt() = command("remindat") {
        description = """
            |`$name <clock time> [message]` 
            |Sets a reminder to fire off and ping you at some later time today.
            |This command is meant to be more convenient than `remindin` for reminders set for the same day. This
            |command needs a `clock time`, which is formatted specifically like `1:43pm` is. The am/pm must be present,
            |and there cannot be spaces. The `message` comes after and is optional, and can be anything you want within
            |500 characters.
            |&{Example usage:}
            |- `remindat 12:00pm bake cookies`\n
            |- `remindat 6:45pm stop playing league and study`\n
            |- `remindat 2:00am`
        """.trimMargin()

        execute(TrClockTime, TrRestJoined.optional("(no message)")) { (clockTime, message) ->
            val zone = ZoneManager.getZone(event.author.id)
            check(zone, "You must set a timezone with the `settz` command!") { this == null } ?: return@execute
            check(message, "Your message can be at most 500 characters!") { length > 500 } ?: return@execute

            val timeAfter = clockTime.toZoned(zone!!)
            check(timeAfter, "That time has already passed!") { isBefore(ZonedDateTime.now(zone)) } ?: return@execute

            val reminder = event.run { Reminder(message, timeAfter, guild.id, channel.id, messageId, author.id) }

            ReminderManager.addReminder(reminder, jda)
            success("I will remind you at `${timeAfter.formatTimeOnly()}`!")
        }
    }

    fun emote() = command("emote") {
        description = """
            |`$name <emote names...>`
            |Sends custom emotes from servers I am in.
            |This command takes from one to twenty (inclusive) `emote names`. I will then try to find an emote with
            |each of those names, sending them. The purpose of this is to bypass server-specific emote usage, as any
            |server which I am in will open up its pool of emotes for use.
            |&{Conflicting names:}
            |If there is more than one emote with the same name, you can add a `~n` to the end of the name, where `n`
            |is an integer. This will make me get the `n`th emote I find with that name.
            |&{Example usage:}
            |- `emote omegalul`\n
            |- `emote pogchamp xd omegalul xd xd`\n
            |- `emote xd~1 xd~2`
        """.trimMargin()

        execute(TrRemaining) { (names) ->
            check(names, "I can only send up to 20 emotes!") { size > 20 } ?: return@execute

            val authorName = event.guild.getMember(event.author)!!.effectiveName
            val emotes = names
                .asSequence()
                .map { it.split('~') }
                .map { if (it.size > 1) Pair(it[0], it[1].toIntOrNull()) else Pair(it[0], 1) }
                .filter { it.second != null }
                .mapNotNull { (name, index) -> jda.getEmotesByName(name, true).getOrNull(index!! - 1) }
                .joinToString(" ") { it.asMention }

            check(emotes, "I can't access any of those emotes!") { isEmpty() } ?: return@execute

            runCatching { event.message.remove() }
            send("**$authorName**: $emotes")
        }
    }

    fun help() = command("help") {
        description = "Shows help text about commands and examples of using them."

        execute(TrWord.optional()) { (commandName) ->
            bot as CommandBot

            send(
                if (commandName == null) {
                    ListHelpSender(bot)
                } else {
                    val command = bot.commandsByName[commandName]
                    check(command, "I can't find that command!") { this == null } ?: return@execute
                    CommandHelpSender(command!!)
                }
            )
        }
    }
}
