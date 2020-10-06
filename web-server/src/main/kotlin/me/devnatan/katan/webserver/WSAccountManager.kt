package me.devnatan.katan.webserver

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import me.devnatan.katan.api.account.Account
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.NoSuchElementException

class WSAccountManager(val webserver: KatanWS) {

    companion object {

        const val AUTH_SECRET_MIN_LENGTH = 8
        const val AUTH_SECRET_MAX_LENGTH = 32
        private val JWT_ACCOUNT_CLAIM = "account"
        private val JWT_TOKEN_LIFETIME = Duration.ofMinutes(10)!!

    }

    private val algorithm: Algorithm
    val verifier: JWTVerifier
    private val audience: String

    init {
        val secret = webserver.config.getString("jwt.secret")
        val len = secret.length
        check(len >= AUTH_SECRET_MIN_LENGTH) {
            "Authentication secret must have at least %d characters (given: %d).".format(
                AUTH_SECRET_MIN_LENGTH, len
            )
        }
        check(len <= AUTH_SECRET_MAX_LENGTH) {
            "Authentication secret cannot exceed %d characters (given: %d).".format(
                AUTH_SECRET_MAX_LENGTH, len
            )
        }

        audience = webserver.config.getString("jwt.audience")
        algorithm = Algorithm.HMAC256(secret)
        verifier = JWT.require(algorithm).withAudience(audience).build()!!
    }

    suspend fun authenticateAccount(account: Account, password: String): String {
        if (!webserver.katan.accountManager.authenticateAccount(account, password))
            throw IllegalArgumentException()

        return JWT.create()
            .withAudience(audience)
            .withClaim(JWT_ACCOUNT_CLAIM, account.id.toString())
            .withExpiresAt(Date.from(Instant.now().plus(JWT_TOKEN_LIFETIME)))
            .sign(algorithm)
    }

    suspend fun verifyPayload(payload: Payload): Account {
        val claim = payload.getClaim(JWT_ACCOUNT_CLAIM)
        if (claim.isNull)
            throw IllegalArgumentException()

        return webserver.katan.accountManager.getAccount(claim.asString()) ?: throw NoSuchElementException()
    }

}