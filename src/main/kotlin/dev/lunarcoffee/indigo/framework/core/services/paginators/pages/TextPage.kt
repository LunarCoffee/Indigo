package dev.lunarcoffee.indigo.framework.core.services.paginators.pages

import net.dv8tion.jda.api.MessageBuilder

class TextPage(private val text: String) : Page {
    override val suppressEmbeds = true

    override fun asMessage(page: Int, totalPages: Int) = MessageBuilder()
        .setContent(if (totalPages == 1) text else "[$page/$totalPages]\n$text")
        .build()
}
