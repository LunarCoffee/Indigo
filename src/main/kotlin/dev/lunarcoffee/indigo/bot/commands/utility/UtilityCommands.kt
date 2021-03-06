package dev.lunarcoffee.indigo.bot.commands.utility

import dev.lunarcoffee.indigo.bot.commands.utility.help.CommandHelpSender
import dev.lunarcoffee.indigo.bot.commands.utility.help.ListHelpSender
import dev.lunarcoffee.indigo.bot.commands.utility.party.PartyManager
import dev.lunarcoffee.indigo.bot.commands.utility.remind.Reminder
import dev.lunarcoffee.indigo.bot.commands.utility.remind.ReminderManager
import dev.lunarcoffee.indigo.bot.commands.utility.reminders.ReminderCancelSender
import dev.lunarcoffee.indigo.bot.commands.utility.reminders.ReminderListSender
import dev.lunarcoffee.indigo.bot.util.*
import dev.lunarcoffee.indigo.framework.api.dsl.command
import dev.lunarcoffee.indigo.framework.api.exts.send
import dev.lunarcoffee.indigo.framework.core.commands.CommandGroup
import dev.lunarcoffee.indigo.framework.core.commands.transformers.*
import java.time.ZoneId
import java.time.ZonedDateTime

@CommandGroup("Utility")
class UtilityCommands {
    fun remindIn() = command("remindin") {
        description = """
            |`$name <delay> [message]`
            |Sets a reminder to fire off and ping you in some amount of time.
            |This command takes a `delay` time string and an optional `message`. After the amount of time specified, I
            |will ping you with your message. The time string is formatted specifically like `3h 30m` or similar (look
            |at the example usages below), and the message can be at most 500 characters.
            |&{Example usage:}
            |- `$name 10h thaw the meat for dinner`\n
            |- `$name 1d 30m 30s hello me`\n
            |- `$name 20m`
        """.trimMargin()

        execute(TrTime, TrRestJoined.optional { "(no message)" }) { (delay, message) ->
            check(message, "Your message can be at most 500 characters!") { length > 500 } ?: return@execute
            check(delay, "Your reminder must be in at least 30 seconds!") { totalSeconds < 30 } ?: return@execute

            val zone = event.author.getUserSettings().zone
            val timeAfter = delay.asTimeFromNow(zone ?: ZoneId.systemDefault())
            val timeString = if (zone == null) "" else timeAfter.formatDefault()

            val reminder = event
                .run { Reminder(message, timeAfter, timeString, guild.id, channel.id, messageId, author.id) }
            ReminderManager.addReminder(reminder, jda)

            success("I will remind you in `$delay`!")
        }
    }

    fun remindAt() = command("remindat") {
        description = """
            |`$name <clock time> [message]` 
            |Sets a reminder to fire off and ping you at some later time within 24 hours.
            |This command is meant to be more convenient than `remindin` for reminders set within 24 hours. This
            |command needs a `clock time`, which is formatted specifically like `1:43pm` is. The am/pm must be present,
            |and there cannot be spaces. The `message` comes after and is optional, and can be anything you want within
            |500 characters.
            |&{Time calculations:}
            |The time at which I will remind you will be the next time your specified `clock time` passes. For example,
            |if you use `remindat 8:00am make coffee` at 10:00 PM, the reminder will fire tomorrow at 8:00 AM. If you 
            |use the same command at 7:00 AM though, it will fire in just one hour.
            |&{Example usage:}
            |- `$name 12:00pm bake cookies`\n
            |- `$name 6:45pm stop playing league and study`\n
            |- `$name 2:00am`
        """.trimMargin()

        execute(TrClockTime, TrRestJoined.optional { "(no message)" }) { (clockTime, message) ->
            val zone = event.author.getUserSettings().zone
            checkNull(zone, "You must set a timezone with the `settz` command!") ?: return@execute
            check(message, "Your message can be at most 500 characters!") { length > 500 } ?: return@execute

            // Use the nearest instance of the time specified.
            val timeAfter = clockTime
                .toZoned(zone!!)
                .run { if (isBefore(ZonedDateTime.now(zone))) plusDays(1) else this }

            val timeString = timeAfter.formatDefault()
            val reminder = event
                .run { Reminder(message, timeAfter, timeString, guild.id, channel.id, messageId, author.id) }
            ReminderManager.addReminder(reminder, jda)

            success("I will remind you at `${timeAfter.formatDefault()}`!")
        }
    }

    fun reminders() = command("reminders", "remindl") {
        description = """
            |`$name ["cancel"] [which]`
            |Shows your reminders or cancels one of them.
            |This command used without arguments (see the first example usage) will list each pending reminder you 
            |have along with a number. If `cancel` and `which` are specified, I will cancel the reminder with number
            |equal to `which`.
            |&{Example usage:}
            |- `$name`\n
            |- `$name cancel 2`
        """.trimMargin()

        execute(TrWord.optional(), TrInt.optional()) { (cancel, which) ->
            if (cancel == null && which == null)
                send(ReminderListSender(event.author.id))
            else if (cancel == "cancel" && which != null)
                send(ReminderCancelSender(event.author.id, which))
            else
                failure("That's not right. Type `${invokedPrefix}help ${this@command.name}` for information.")
        }
    }

    fun party() = command("party") {
        description = """
            |`$name name ["leave"|"disband"]`
            |Joins (or creates), leaves, or disbands a party.
            |A party is a group of members. If no party of `name` exists, one will be created and you will be added to 
            |it. When joining, all members of the party are mentioned. When leaving or disbanding, no one is mentioned.
            |If there are no members in a party, it is automatically disbanded.
            |&{Long term usage:}
            |Parties are not persisted when the bot restarts. As such, you should not depend on this feature in the
            |long term. If that is what you wish to do, consider using roles instead.
            |&{Example usage:}
            |- `$name lol`\n
            |- `$name lol leave`\n
            |- `$name lol disband`
        """.trimMargin()

        execute(TrWord, TrWord.optional()) { (rawName, action) ->
            val name = rawName.toLowerCase()
            val member = event.member!!

            when (action) {
                null -> {
                    checkNull(PartyManager.joinOrCreate(name, member), "You are already in the `$name` party!")
                        ?: return@execute

                    val party = PartyManager.get(name)!!
                    if (party.size == 1) {
                        success("You have created the `$name` party!")
                    } else {
                        val members = party.dropLast(1).joinToString(" ") { it.asMention }
                        success("`${event.member!!.effectiveName}` has joined the $name party!\n$members")
                    }
                }
                "leave" -> {
                    checkNull(PartyManager.leave(name, member), "You are not in the `$name` party!") ?: return@execute
                    if (PartyManager.get(name) == null)
                        success("You have left the `$name` party which has now been disbanded!")
                    else
                        success("You have left the `$name` party!")
                }
                "disband" -> {
                    when (PartyManager.disband(name, member)) {
                        true -> success("The `$name` party has been disbanded!")
                        false -> failure("You must be in the `$name` party to disband it!")
                        null -> failure("There is no party called `$name`!")
                    }
                }
                else -> failure("That's not right. Type `${invokedPrefix}help ${this@command.name}` for information.")
            }
        }
    }

    fun help() = command("help", "info") {
        description = """
            |`$name [command name]`
            |Shows help text about commands and examples of using them.
            |This command, without a `command name`, will show all my command groups with their commands. With a name,
            |this will show information about the command, including its aliases (names which you can use in place of
            |the normal name), short description, usage string, and extended description.
            |&{Reading usage strings:}
            |Usually, the example usages are good enough, but for exactly how you can use a command, the usage string
            |can help. Each usage string will start with the command name, then have a list of arguments. These
            |arguments are usually just formatted strings of text that mean something, like a number or name. These
            |characters have special meaning:\n
            |- `<arg>`: required argument\n
            |- `[arg]`: optional argument\n
            |- `arg...`: one or more of the argument\n
            |- `"arg"`: that specific string of text within the quotes\n
            |- `arg1|arg2`: either arg1 or arg2\n
            |These can be combined, as with `[arg...]` (one or more of an optional argument), for instance.
            |&{Example usage:}
            |- `$name ping`\n
            |- `$name`
        """.trimMargin()

        execute(TrWord.optional()) { (commandName) ->
            send(
                if (commandName == null) {
                    ListHelpSender(bot, isAuthorOwner(bot))
                } else {
                    val command = bot.commandsByName[commandName]
                    checkNull(command, "I can't find that command!") ?: return@execute
                    CommandHelpSender(command!!)
                }
            )
        }
    }
}
