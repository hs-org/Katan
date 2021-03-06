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

package me.devnatan.katan.api.server.composition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * The server compositions factory is responsible for generating and identifying
 * the compositions and also for generating your options when loading them.
 *
 * A factory can store multiple compositions, and resolve them simultaneously.
 */
abstract class CompositionFactory(
    internal val registrations: MutableMap<String, Composition.Key> = hashMapOf()
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val channel = BroadcastChannel<CompositionPacket>(Channel.BUFFERED)

    /**
     * Returns a composition instance created from its key.
     * @param key the composition key.
     */
    abstract suspend fun create(key: Composition.Key): Composition<CompositionOptions>

    /**
     * Returns the data generated for a composition.
     * @param key the composition key.
     * @param data the data that was saved from that composition.
     */
    open suspend fun generate(key: Composition.Key, data: Map<String, Any>): CompositionOptions {
        return DummyCompositionOptions
    }

}

object DummyCompositionOptions : CompositionOptions

/**
 * Returns the registered key with the specified [name] or `null` if the key is not found.
 */
operator fun CompositionFactory.get(name: String): Composition.Key? {
    return registrations[name]
}

/**
 * Returns the name for the [key] or `null` if the key has not been registered.
 */
operator fun CompositionFactory.get(key: Composition.Key): String? {
    return registrations.entries.firstOrNull { it.value == key }?.key
}

/**
 * Register a new [key] with the specified [name].
 */
operator fun CompositionFactory.set(name: String, key: Composition.Key) {
    registrations[name] = key
}

/**
 * Register a new [key] with the specified [name].
 */
operator fun CompositionFactory.set(key: Composition.Key, name: String) {
    registrations[name] = key
}

/**
 * Register a new [key].
 */
fun CompositionFactory.registerKey(key: Composition.Key) {
    registrations[key.name] = key
}

/**
 * Register a new [key] with the specified [name].
 */
fun CompositionFactory.registerNamedKey(name: String, key: Composition.Key) {
    registrations[name] = key
}

/**
 * Register a new [key] with the specified [name].
 */
fun CompositionFactory.registerNamedKey(key: Composition.Key, name: String) {
    registrations[name] = key
}

/**
 * Sends a prompt to the CLI requesting information and suspending the function until that information is obtained.
 * @param text the text to be displayed on the console.
 * @param defaultValue the default value to be returned if there is no input.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun CompositionFactory.prompt(text: String, defaultValue: String? = null): String {
    val job = CompletableDeferred<String>()
    val packet = CompositionPacket.Prompt(text, defaultValue, job)
    channel.send(packet)
    return job.await()
}

/**
 * Sends a message to the Command Line Interface (CLI).
 * @param text the message to be sent.
 * @param error if it is an error message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun CompositionFactory.message(text: String, error: Boolean = false) {
    channel.send(CompositionPacket.Message(text, error))
}

/**
 * Forces the end of the current composition cycle
 * in the Command Line Interface (CLI).
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend inline fun CompositionFactory.close() {
    channel.send(CompositionPacket.Close)
}

/**
 * Attempts to generate the options for a composition automatically based on the data.
 * @param type the type of the composition options.
 * @param data the data to be set.
 */
fun <T : CompositionOptions> useAutoGeneratedOptions(type: KClass<T>, data: Map<String, Any>): T {
    val caller = type.primaryConstructor!!
    val values = hashMapOf<KParameter, Any?>()
    for (parameter in caller.parameters) {
        val value = data[parameter.name]

        @Suppress("UNCHECKED_CAST")
        val result = if (value is Map<*, *>) useAutoGeneratedOptions(
            parameter.type.jvmErasure as KClass<T>,
            value as Map<String, Any>
        ) else value
        values[parameter] = result
    }
    return caller.callBy(values)
}


/**
 * Attempts to generate the options for a composition automatically based on the data.
 * @param T the type of the composition options.
 * @param data the data to be set.
 */
inline fun <reified T : CompositionOptions> useAutoGeneratedOptions(data: Map<String, Any>): T {
    return useAutoGeneratedOptions(T::class, data)
}