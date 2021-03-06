/*
 * Copyright 2020-present Natan Vieira do Nascimento
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.devnatan.katan.api.plugin.command

import kotlinx.coroutines.CoroutineDispatcher

typealias CommandExecutor = suspend CommandExecutionContext.() -> Unit

private class Context(
    override val label: String,
    override val args: Array<out String>
) : CommandExecutionContext

private class KatanCommandImpl(
    name: String,
    aliases: List<String>,
    override val dispatcher: CoroutineDispatcher?,
    private inline val executor: CommandExecutor
): KatanCommand(name, aliases) {

    override suspend fun execute(label: String, args: Array<out String>) {
        executor(Context(label, args))
    }

}

class CommandBuilder(val name: String) {

    var aliases: List<String> = emptyList()
    var dispatcher: CoroutineDispatcher? = null
    var executor: CommandExecutor? = null
    var commands: MutableList<CommandBuilder> = arrayListOf()

    fun toCommand(): Command {
        return KatanCommandImpl(name, aliases, dispatcher, executor ?: {}).apply {
            for (subcommand in commands)
                addCommand(subcommand.toCommand())
        }
    }

    fun execute(block: CommandExecutor) {
        this.executor = block
    }

    inline fun command(name: String, vararg aliases: String, crossinline block: CommandBuilder.() -> Unit) {
        command(name, aliases.toList(), block).also { commands.add(it) }
    }

}

inline fun command(name: String, vararg aliases: String, crossinline block: CommandBuilder.() -> Unit): Command {
    return command(name, aliases.toList(), block).apply(block).toCommand()
}

inline fun command(name: String, aliases: List<String>, crossinline block: CommandBuilder.() -> Unit): CommandBuilder {
    return CommandBuilder(name).apply {
        this.aliases = aliases.toList()
    }.apply(block)
}