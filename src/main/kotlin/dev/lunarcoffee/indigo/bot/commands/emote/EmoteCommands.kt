package dev.lunarcoffee.indigo.bot.commands.emote

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitByteArray
import dev.lunarcoffee.indigo.bot.util.consts.Emoji
import dev.lunarcoffee.indigo.bot.util.sanitize
import dev.lunarcoffee.indigo.bot.util.success
import dev.lunarcoffee.indigo.framework.api.dsl.command
import dev.lunarcoffee.indigo.framework.api.dsl.paginator
import dev.lunarcoffee.indigo.framework.api.exts.*
import dev.lunarcoffee.indigo.framework.core.commands.CommandGroup
import dev.lunarcoffee.indigo.framework.core.commands.GuildCommandContext
import dev.lunarcoffee.indigo.framework.core.commands.transformers.*
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Icon

@CommandGroup("Emote")
class EmoteCommands {
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
            |- `$name omegalul`\n
            |- `$name pogchamp xd omegalul xd xd`\n
            |- `$name xd~1 xd~2`
        """.trimMargin()

        execute(TrRemaining) { (names) ->
            check(names, "I can only send up to 20 emotes!") { size > 20 } ?: return@execute

            var authorName = event.guild.getMember(event.author)!!.effectiveName.sanitize()
            authorName = authorName.replace("\\@", "")
            val emotes = names
                .asSequence()
                .map { emoteNameIndexPair(it) }
                .filter { it.second != null }
                .mapNotNull { (name, index) -> jda.getEmotesByName(name, true).getOrNull(index!! - 1) }
                .joinToString(" ") { it.asMention }

            check(emotes, "I can't access any of those emotes!") { isEmpty() } ?: return@execute

            runCatching { event.message.remove() }
            send("**$authorName**: $emotes")
        }
    }

    fun scan() = command("scan", "scanemotes") {
        description = """
            |`$name [message limit]`
            |Takes all custom emotes from the last few messages.
            |This command will find custom emotes from the last `message limit` messages. If a limit is not specified,
            |I will look in the last 100 messages. I will then PM you with the names and image links to each emote.
            |&{Example usage:}
            |- `$name`\n
            |- `$name 500`
        """.trimMargin()

        execute(TrInt.optional { 100 }) { (limit) ->
            check(limit, "I can only scan up to the last 1000 message!") { this !in 1..1_000 } ?: return@execute

            val emotes = event
                .channel
                .iterableHistory
                .take(limit + 1)
                .drop(1)
                .flatMap { it.emotes }
                .distinctBy { it.id }

            if (emotes.isEmpty()) {
                success("There were no emotes used in the last `$limit` messages!")
                return@execute
            }

            success("I am sending you your emotes!")

            val pmChannel = event.author.openPrivateChannel().await()
            pmChannel.send(
                pmChannel.paginator {
                    for (emotePage in emotes.chunked(16)) {
                        embedPage {
                            title = "${Emoji.DETECTIVE}  Here are your emotes:"
                            description = emotePage.joinToString("\n") { "**${it.name}**: [link](${it.imageUrl})" }
                        }
                    }
                }
            )
        }
    }

    fun steal() = command("steal", "stemt", "stealemote") {
        description = """
            |`$name [emote name]`
            |Gets and automatically adds an emote to your server.
            |This command will look for the specified emote with name `emote name`. If it is not specified, I will take
            |the last used emote in the current channel. I will then try to add it to the server. Note that I am only 
            |able to access emotes from servers I am in, or which have been recently used (what qualifies is loosely
            |defined).
            |&{Conflicting names:}
            |If there is more than one emote with the same `emote name`, you can add a `~n` to the end of the name, 
            |where `n` is an integer. This will make me get the `n`th emote I find with that name.
            |&{Example usage:}
            |- `$name`\n
            |- `$name myNiceEmote`
        """.trimMargin()

        execute(TrWord.optional()) { (name) ->
            canManageEmotes() ?: return@execute

            val emote = if (name == null) {
                // Take the first emote in the first message with emotes in the last 100 messages.
                val emote = event.channel.iterableHistory.take(100).find { it.emotes.isNotEmpty() }?.emotes?.get(0)
                check(emote, "There are no emotes in the last 100 messages!") { this == null } ?: return@execute
                emote
            } else {
                val (newName, index) = emoteNameIndexPair(name)
                checkNull(index, "You provided an invalid index on the emote name!") ?: return@execute

                val emote = jda.getEmotesByName(newName, true).getOrNull(index!! - 1)
                checkNull(this, "I can't access that emote!") ?: return@execute
                emote
            }

            val iconData = runCatching { Fuel.get(emote!!.imageUrl).timeout(3_000).awaitByteArray() }.getOrNull()
            checkNull(iconData, "Network request timed out. Discord or my connection is unstable.") ?: return@execute

            tryAddEmote(emote!!.name, Icon.from(iconData!!)) ?: return@execute
            success("Your emote has been added!")
        }
    }

    fun imageEmote() = command("imageemote", "imgemt") {
        description = """
            |`$name [name]`
            |Gets and automatically adds the last image posted as an emote to your server.
            |This command will look for an image in the last 100 messages. Note that this image must be uploaded, not
            |embedded from a link. I will then try to add the image to your server as an emote with the given `name`,
            |or the file name without the extension if no name is provided.
            |&{Example usage:}
            |- `$name`\n
            |- `$name kekw`
        """.trimMargin()

        execute(TrWord.optional()) { (name) ->
            canManageEmotes() ?: return@execute

            val image = event
                .channel
                .iterableHistory
                .take(100)
                .find { msg -> msg.attachments.any { it.isImage } }
                ?.attachments
                ?.find { it.isImage }

            checkNull(image, "I can't find an image in the last 100 messages!") ?: return@execute
            check(image!!, "That image is too large!") { size >= 262_144 } ?: return@execute

            val emoteName = name ?: image.fileName.substringBeforeLast('.').take(32)
            check(emoteName, "That emote name is invalid!") { ' ' in this } ?: return@execute

            val imageIcon = image.retrieveAsIcon().await()
            tryAddEmote(emoteName, imageIcon!!) ?: return@execute
            success("Your emote has been added!")
        }
    }

    private suspend fun GuildCommandContext.canManageEmotes(): Unit? {
        checkPermission("You must be able to manage emotes!", Permission.MANAGE_EMOTES) ?: return null
        checkPermission("I must be able to manage emotes!", Permission.MANAGE_EMOTES, event.guild.selfMember)
            ?: return null
        return Unit
    }

    // Splits a name like `blobWave~2` into a pair (blobWave, 2).
    private fun emoteNameIndexPair(name: String): Pair<String, Int?> {
        val split = name.split('~')
        return if (split.size > 1) Pair(split[0], split[1].toIntOrNull()) else Pair(split[0], 1)
    }

    private suspend fun GuildCommandContext.tryAddEmote(name: String, icon: Icon): Unit? {
        val add = runCatching { event.guild.createEmote(name, icon).await() }.getOrNull()
        return checkNull(add, "Your server is probably out of emote slots!")
    }
}
