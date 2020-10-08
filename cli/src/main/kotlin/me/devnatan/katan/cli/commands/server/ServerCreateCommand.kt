package me.devnatan.katan.cli.commands.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import kotlinx.coroutines.*
import me.devnatan.katan.api.server.Server
import me.devnatan.katan.api.server.ServerComposition
import me.devnatan.katan.api.server.get
import me.devnatan.katan.cli.KatanCLI
import me.devnatan.katan.common.server.UninitializedServer

class ServerCreateCommand(private val cli: KatanCLI) : CliktCommand(
    name = "create",
    help = "Creates a new server"
) {

    private val name by option("-n", "--name", help = "Server name").required()
    private val image by option("-i", "--image")
    private val composer by option("-c", "--composer")
    private val compositions by option(
        "-w",
        "--with",
        help = "List of composition definitions to be applied to that server"
    ).split(",").default(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    override fun run() {
        if (cli.serverManager.existsServer(name)) {
            echo("There is already a server with that name, try another one.", err = true)
            echo("Use \"katan server ls\" to find out which servers already exist.", err = true)
            return
        }

        var server: Server
        runBlocking {
            echo("Creating server \"$name\"...")
            server = cli.serverManager.createServer(UninitializedServer(name))
            cli.serverManager.addServer(server)

            cli.coroutineScope.launch(cli.coroutineExecutor + CoroutineName("KatanCLI::server-register")) {
                echo("Registering server....")
                cli.serverManager.registerServer(server)
            }.join()
        }

        val supervisor = SupervisorJob()
        supervisor.invokeOnCompletion {
            cli.coroutineScope.launch(Dispatchers.IO + CoroutineName("KatanCLI::server-inspection")) {
                echo("Finishing inspecting server...")
                cli.serverManager.inspectServer(server)
            }.invokeOnCompletion {
                if (it == null)
                    echo("Server $name created successfully!")
                else
                    echo("Failed to inspect the server: $it")
            }
        }

        if (compositions.isNotEmpty()) {
            echo("Applying compositions...")

            val length = compositions.size
            val applied = arrayListOf<ServerComposition.Key<*>>()
            for ((index, name) in compositions.withIndex()) {
                val phase = "[${index + 1}/$length]"
                val factory = cli.serverManager.getCompositionFactoryApplicableFor(name)
                if (factory == null) {
                    echo("$phase Factory not found for composition $name.", err = true)
                    continue
                }

                val key = factory[name]
                if (key == null) {
                    echo("$phase $name is registered but not applicable for the specified this factory", err = true)
                    continue
                }

                if (key.default) {
                    echo(
                        "$phase The $name composition is applied by default, it cannot be defined explicitly.",
                        err = true
                    )
                    continue
                }

                val single = applied.firstOrNull { it.single }
                if (single != null && key.single) {
                    echo(
                        "$phase The composition \"$name\" is unique, it cannot perform together with \"${single.name}\", which is also unique.",
                        err = true
                    )
                    continue
                }

                echo("$phase Composing with \"${name}\"...")
                /* val composition: ServerComposition<ServerCompositionOptions> = factory.create(key, server)
                val adapter = factory.adapter

                /*
                    We need the function __to be executed outside the main thread__ (which runs the CLI),
                    so we launched it on another thread but there are packets received from the adapter
                    that suspend and only return after the result, which is the case of the prompt.

                    So we would need to have the result to continue the execution of the compositions
                    applications, but to have the result we would need to apply the adapters that only
                    suspend after the result which ~~consequently will only return if we apply the compositions~.
                 */
                val channel = adapter.channel.openSubscription()

                // Use the UNDISPATCHED start to send the handling of the prompt back to the main thread.
                val job = cli.coroutineScope.launch(Dispatchers.IO, CoroutineStart.LAZY) {
                    composition.factory = factory
                    echo("$phase Applying composition $name...")
                    composition.options = factory.adapter.apply(key, server)
                    echo("$phase Composition $name applied, writing...")
                    composition.write(server)
                    echo("$phase Completed, next!")
                }

                val collector =

                cli.coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    /*
                        This block will execute before the beginning of the child process,
                        in the main thread being possible to handle the prompt and when finished
                        the child will be executed applying the composition.
                     */
                    val flow = channel.consumeAsFlow().buffer()
                    flow.onEach { packet ->
                        echo("$phase Collect [${packet::class.simpleName?.toUpperCase()}] packet for $name.")
                        when (packet) {
                            is ServerCompositionPacket.Prompt -> {
                                val defer = packet.job
                                prompt(packet.text, packet.defaultValue)?.let {
                                    defer.complete(it)
                                } ?: defer.completeExceptionally(NullPointerException())
                            }
                            is ServerCompositionPacket.Message -> echo(packet.content)
                        }
                    }

                    /*
    Starting a lazy coroutine here causes it to run after the next block.
    If we tried to leave the job without LAZY inline, it would not work, the
    function would simply suspend the parent job until the child's termination,
    but we need the father to run first and the child later.
 */
                    job.start()
                    echo("$phase Job started for $name.")

                    echo("$phase Flow passed for $name.")
                    applied.add(key)
                }

                echo("Runblocking finished...")
            }

            // echo("Compositions completed")
            // supervisor.complete() */
            }
        }
    }

}