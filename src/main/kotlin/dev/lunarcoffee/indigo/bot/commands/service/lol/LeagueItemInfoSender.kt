package dev.lunarcoffee.indigo.bot.commands.service.lol

import com.merakianalytics.orianna.types.common.Region
import com.merakianalytics.orianna.types.core.staticdata.Item
import dev.lunarcoffee.indigo.bot.util.consts.Emoji
import dev.lunarcoffee.indigo.bot.util.distance
import dev.lunarcoffee.indigo.bot.util.ifNullNone
import dev.lunarcoffee.indigo.framework.api.dsl.embed
import dev.lunarcoffee.indigo.framework.api.exts.send
import dev.lunarcoffee.indigo.framework.core.commands.CommandContext
import dev.lunarcoffee.indigo.framework.core.std.ContentSender

class LeagueItemInfoSender(private val itemNames: List<String>) : ContentSender {
    override suspend fun send(ctx: CommandContext) {
        // Try to get the item given [itemNames]. If that fails, take the first item with distance less than four from
        // any of the [itemNames]. If that fails, error out.
        val item = itemNames
            .map { Item.named(it).withRegion(Region.NORTH_AMERICA).get() }
            .firstOrNull { it.stats != null }
            ?: LeagueInfo.allItems.firstOrNull { item -> itemNames.any { it.distance(item.name) < 4 } }
        ctx.checkNull(item, "That item does not exist!") ?: return

        ctx.send(
            embed {
                title = "${Emoji.BOOK}  Info on the item **${item!!.name}**:"
                thumbnail = item.image.url

                val itemBasePrice = if (item.basePrice != item.totalPrice) " (${item.basePrice})" else ""
                description = """
                    |**Buy price**: ${item.totalPrice}${itemBasePrice} gold
                    |**Sell price**: ${item.sellPrice} gold
                    |**Builds from**: ${item.buildsFrom?.map { "${it.name} (${it.totalPrice})" }.ifNullNone()}
                    |**Builds into**: ${item.buildsInto?.map { it.name }.ifNullNone()}
                """.trimMargin()

                // Fields for item stats, passives, actives, auras, and consumables.
                for ((fieldName, entries) in item.description.parseItemDescription())
                    field(fieldName, entries.joinToString("\n"))
            }
        )
    }

    private fun String.parseItemDescription(): List<Pair<String, List<String>>> {
        val stats = statRegex
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.split("<br>")
            ?.map { it.replace(LeagueInfo.tagRegex, "") }
            ?: emptyList()

        // This terrible mess removes tags which we do not parse here (unique, aura, consumable). First, the tags to
        // keep are substituted to something else that does not look like a tag. Next, all tags are removed, except for
        // `<br>` elements which are replaced by a space. Finally, the first substitution is undone.
        var newStr = substitutions.fold(this) { acc, (before, after) -> acc.replace(before, after) }
        newStr = newStr.replace(LeagueInfo.lineBreakRegex, " ").replace(LeagueInfo.tagRegex, "")
        newStr = substitutions.fold(newStr) { acc, (before, after) -> acc.replace(after, before) }

        val actives = activeRegex.findAll(newStr).map { "**${it.groupValues[2]}**:${it.groupValues[4]}" }
        val auras = auraRegex.findAll(newStr).map { "**${it.groupValues[1]}**:${it.groupValues[2]}" }
        val consumables = consumableRegex.findAll(newStr).map { "**${it.groupValues[1]}**:${it.groupValues[2]}" }
        val passives = passiveRegex.findAll(newStr).map { "**${it.groupValues[2]}**:${it.groupValues[4]}" }

        return listOf(
            "Stats" to stats,
            "Passives" to passives.toList(),
            "Actives" to actives.toList(),
            "Auras" to auras.toList(),
            "Consumables" to consumables.toList()
        ).filter { it.second.isNotEmpty() }
    }

    companion object {
        private val substitutions = listOf("<unique>", "<aura>", "<consumable>", "<active>", "<passive>")
            .flatMap { listOf(it, "</${it.drop(1)}") }
            .map { it to it.replace("<", "{").replace(">", "}") }

        private val statRegex = "<stats>(.+)</stats>".toRegex()
        private val activeRegex = "<(unique|active)>(UNIQUE Active[^:]*):</(unique|active)>([^<]+)(<|$)".toRegex()
        private val auraRegex = "<aura>([^:]+):</aura>([^<]+)(<|$)".toRegex()
        private val consumableRegex = "<consumable>([^:]+):</consumable>([^<]+)(<|$)".toRegex()
        private val passiveRegex = "<(unique|passive)>(UNIQUE Passive[^:]*):</(unique|passive)>([^<]+)(<|$)"
            .toRegex()
    }
}
