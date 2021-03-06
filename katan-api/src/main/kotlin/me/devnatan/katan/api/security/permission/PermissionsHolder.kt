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

package me.devnatan.katan.api.security.permission


/**
 * Entities holding permissions are based on their list of [permissions]
 * defined solely from themselves.
 *
 * In the application lifecycle, having an entity holding permissions means
 * there are areas protected from specific actions by external members such as
 * [me.devnatan.katan.api.security.UntrustedProvider].
 *
 * Permissions are indexed and assigned to these entities from their
 * [PermissionKey] and these keys have no distinction between the standards
 * provided natively by Katan or whether they were provided by an external
 * member.
 *
 * @apiNote
 * All standard functions are for self-checking only.
 *
 * @implNote
 * In case of need for hierarchical verification, it must be carried out by
 * the implementation itself, but also considering the unique values of the
 * [PermissionKey].
 *
 * @author Natan Vieira
 * @since  1.0
 */
interface PermissionsHolder : Iterable<Permission> {

    val permissions: List<Permission>

    /**
     * Returns a [Permission] or null if the permission is not defined.
     * @param key the permission key.
     */
    fun getPermission(key: PermissionKey): Permission? {
        return permissions.find { it.key == key }
    }

    /**
     * Returns `true` if that entity has the [Permission] linked to the
     * specified [key] defined for it or `false` otherwise.
     * @param key the permission key.
     */
    fun hasPermission(key: PermissionKey): Boolean {
        return permissions.any { it.key == key }
    }

    /**
     * Returns `true` if that entity has the [Permission] linked to the
     * specified  [key] defined for it with the value
     * [PermissionFlag.ALLOWED] or `false` otherwise it is not defined or if
     * the [Permission.value] is [PermissionFlag.NOT_ALLOWED].
     * @param key the permission key.
     */
    fun isPermissionAllowed(key: PermissionKey): Boolean {
        return getPermission(key)?.value?.isAllowed() ?: false
    }

    /**
     * Sets the [Permission] value linked to [key] to [value].
     * @param key   the permission key.
     * @param value the permission value.
     * @return      The newly defined or modified permission.
     */
    fun setPermission(key: PermissionKey, value: PermissionFlag): Permission

    /**
     * Returns a [Iterator] of all [Permission]s of this holder.
     */
    override fun iterator(): Iterator<Permission> {
        return permissions.iterator()
    }

}