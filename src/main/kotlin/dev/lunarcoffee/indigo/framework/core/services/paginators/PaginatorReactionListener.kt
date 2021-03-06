package dev.lunarcoffee.indigo.framework.core.services.paginators

import dev.lunarcoffee.indigo.framework.api.exts.await
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

object PaginatorReactionListener : ListenerAdapter() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onMessageReactionAdd(event: MessageReactionAddEvent) {
        if (!PaginatorManager.isPaginatorOwner(event.user?.id ?: return, event.messageIdLong))
            return

        val emoji = event.reaction.reactionEmote.takeIf { it.isEmoji }?.emoji ?: return
        val button = PaginatorButton.values().find { it.char == emoji } ?: return

        PaginatorManager.handleButtonClick(event.messageIdLong, button)

        coroutineScope.launch {
            delay(350)
            if (event.channelType != ChannelType.PRIVATE)
                event.reaction.removeReaction(event.user ?: return@launch).await()
        }
    }
}
