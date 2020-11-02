package me.devnatan.katan.core.impl.server

import me.devnatan.katan.api.game.GameType
import me.devnatan.katan.api.game.GameVersion
import me.devnatan.katan.api.server.ServerGame

data class ServerGameImpl(
    override val type: GameType,
    override val version: GameVersion?
) : ServerGame