package dev.lunarcoffee.indigo.framework.core.commands

interface Command {
    val name: String
    val aliases: List<String>
    val description: String?

    val execute: CommandBody<out CommandArguments>
}
