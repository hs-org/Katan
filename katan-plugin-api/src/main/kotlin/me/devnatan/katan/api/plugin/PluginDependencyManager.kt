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

package me.devnatan.katan.api.plugin

import me.devnatan.katan.api.Descriptor
import me.devnatan.katan.api.Version
import me.devnatan.katan.api.annotations.InternalKatanApi
import me.devnatan.katan.api.service.ServiceDescriptor
import kotlin.reflect.KClass

/**
 * Plugin dependency manager, responsible for loading, adding and removing plugin dependencies.
 * This can also be intertwined with another dependency management system.
 */
interface PluginDependencyManager {

    /**
     * Adds the dependency from a descriptor.
     * @param descriptor the dependency descriptor.
     */
    fun addDependency(descriptor: Descriptor): PluginDependency

    /**
     * Removes a dependency that has been previously added.
     * @param dependency the dependency descriptor.
     */
    fun removeDependency(dependency: PluginDependency)

    /**
     * Resolves a dependency value.
     * @param classifier the dependency value classifier.
     */
    fun resolveDependency(classifier: KClass<*>): Any?

}

/**
 * Adds a plugin that matches the specified [descriptor] by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param descriptor the dependency descriptor.
 */
inline fun PluginDependencyManager.plugin(
    descriptor: PluginDescriptor,
    optional: Boolean = false,
    crossinline block: PluginDependency.() -> Unit = {}
): PluginDependency {
    return addDependency(descriptor).apply {
        this.isOptional = optional
    }.apply(block)
}

/**
 * Adds a plugin that matches the specified descriptor [name] by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param name the dependency name.
 */
inline fun PluginDependencyManager.plugin(
    name: String,
    optional: Boolean = false,
    crossinline block: PluginDependency.() -> Unit = {}
): PluginDependency {
    return plugin(PluginDescriptor(name), optional, block)
}

/**
 * Adds a plugin that matches the specified descriptor [name] and [version] by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param name the dependency name.
 * @param version the dependency version.
 */
inline fun PluginDependencyManager.plugin(
    name: String,
    version: CharSequence,
    optional: Boolean = false,
    crossinline block: PluginDependency.() -> Unit = {}
): PluginDependency {
    return plugin(PluginDescriptor(name, Version(version)), optional, block)
}

/**
 * Adds a plugin that matches the specified descriptor [name] and [version] by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param name the dependency name.
 * @param version the dependency version.
 */
inline fun PluginDependencyManager.plugin(
    name: String,
    version: Version,
    optional: Boolean = false,
    crossinline block: PluginDependency.() -> Unit = {}
): PluginDependency {
    return plugin(PluginDescriptor(name, version), optional, block)
}

/**
 * Adds a service that matches the specified descriptor [classifier] by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param classifier the dependency classifier
 */
@OptIn(InternalKatanApi::class)
fun PluginDependencyManager.service(
    classifier: KClass<out Any>,
    optional: Boolean = false
): PluginDependency {
    return addDependency(ServiceDescriptor(classifier)).apply {
        this.isOptional = optional
    }
}

/**
 * Adds a service that matches the specified descriptor classifier by adding
 * it to the plugin's classpath and setting it as the plugin's pre-boot priority.
 * @param T the dependency classifier
 */
inline fun <reified T : Any> PluginDependencyManager.service(optional: Boolean = false): PluginDependency {
    return service(T::class, optional)
}

class GenericPluginDependencyManager : PluginDependencyManager {

    private val dependencies = HashSet<PluginDependency>()

    override fun addDependency(descriptor: Descriptor): PluginDependency {
        return PluginDependency(descriptor).also {
            dependencies.add(it)
        }
    }

    override fun removeDependency(dependency: PluginDependency) {
        dependencies.remove(dependency)
    }

    @OptIn(InternalKatanApi::class)
    override fun resolveDependency(classifier: KClass<*>): Any? {
        for (dependency in dependencies) {
            val condition = when (dependency.descriptor) {
                is PluginDescriptor -> {
                    runCatching {
                        val computed = dependency.value
                        if (computed == null)
                            false
                        else
                            computed.invoke()!!::class == classifier
                    }.getOrDefault(false)
                }
                is ServiceDescriptor -> {
                    classifier == dependency.descriptor.classifier
                }
                else -> throw IllegalArgumentException(
                    "Unsupported descriptor: ${dependency.descriptor}."
                )
            }

            if (condition)
                return dependency.value?.invoke()
        }

        throw IllegalArgumentException("Unable to resolve classifier: $classifier")
    }

}