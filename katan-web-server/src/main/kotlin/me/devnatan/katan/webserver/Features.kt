package me.devnatan.katan.webserver

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.websocket.*
import me.devnatan.katan.api.defaultLogLevel
import me.devnatan.katan.api.security.account.Account
import me.devnatan.katan.api.server.Server
import me.devnatan.katan.api.server.ServerHolder
import me.devnatan.katan.common.util.get
import me.devnatan.katan.webserver.exceptions.KatanHTTPException
import me.devnatan.katan.webserver.jwt.AccountPrincipal
import me.devnatan.katan.webserver.serializers.AccountSerializer
import me.devnatan.katan.webserver.serializers.InstantSerializer
import me.devnatan.katan.webserver.serializers.ServerHolderSerializer
import me.devnatan.katan.webserver.serializers.ServerSerializer
import java.time.Instant

fun Application.installFeatures(ws: KatanWS) {
    install(Locations)
    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(WebSockets)

    install(ContentNegotiation) {
        jackson {
            propertyNamingStrategy = PropertyNamingStrategy.KEBAB_CASE
            deactivateDefaultTyping()
            enable(SerializationFeature.CLOSE_CLOSEABLE)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)

            registerModule(SimpleModule("Katan").apply {
                addSerializer(Account::class.java, AccountSerializer())
                addSerializer(Instant::class.java, InstantSerializer())
                addSerializer(
                    Server::class.java,
                    ServerSerializer()
                )
                addSerializer(
                    ServerHolder::class.java,
                    ServerHolderSerializer()
                )
            })
        }
    }

    install(CallLogging) {
        if (ws.config.get("logging", true))
            level = ws.katan.environment.defaultLogLevel()
    }

    install(StatusPages) {
        exception<KatanHTTPException> { cause ->
            call.respond(
                cause.status, mapOf(
                    "response" to "error",
                    "code" to cause.response.first,
                    "message" to cause.response.second
                )
            )
        }
    }

    install(CORS) {
        method(HttpMethod.Options)
        header("Authorization")
        allowNonSimpleContentTypes = true
        allowCredentials = true

        val cors = ws.config.getConfig("cors")
        if (cors.get("allowAnyHost", false)) {
            log.info("All hosts have been allowed through CORS.")
            anyHost()
        } else if (cors.hasPath("hosts")) {
            log.info(
                "The following hosts ${
                    cors.getConfigList("hosts").map { config ->
                        Triple(
                            config.getString("hostname"),
                            config.get("schemes", emptyList<String>()),
                            config.get("subDomains", emptyList<String>())
                        )
                    }.onEach { (hostname, schemes, subdomains) ->
                        host(hostname, schemes, subdomains)
                    }
                        .joinToString(", ") { (hostname, schemes, subDomains) ->
                            buildString {
                                append(
                                    schemes.joinToString(
                                        ", ",
                                        prefix = "(",
                                        postfix = ")"
                                    )
                                )
                                append("://")

                                if (subDomains.isNotEmpty()) {
                                    append(
                                        subDomains.joinToString(
                                            ", ",
                                            prefix = "[",
                                            postfix = "]"
                                        )
                                    )
                                    append(".")
                                }

                                append(hostname)
                            }
                        }
                } have been allowed through in CORS. "
            )
        }
    }

    install(Authentication) {
        jwt {
            realm = "Katan WebServer"
            verifier(ws.tokenManager.verifier)

            validate { credential ->
                val account =
                    ws.tokenManager.verifyPayload(credential.payload)
                        ?: respondWithError(
                            INVALID_ACCESS_TOKEN_ERROR,
                            HttpStatusCode.Unauthorized
                        )

                AccountPrincipal(account)
            }
        }
    }

    install(DataConversion) {
        convert<Server> {
            encode {
                if (it == null) emptyList()
                else listOf((it as Server).id.toString())
            }
            decode { values, _ ->
                ws.katan.serverManager.getServer(
                    values.single().toInt()
                )
            }
        }
    }

    if (ws.config.get(
            "deployment.secure",
            false
        ) && ws.config.hasPath("deployment.sslPort")
    ) {
        install(HttpsRedirect) {
            sslPort = ws.config.getInt("deployment.sslPort")
            permanentRedirect = ws.config.get("https-redirect", true)
        }
    }

    if (ws.config.get("hsts", true)) {
        install(HSTS)
        log.info("Enabled Strict Transport Security (HSTS).")
    }

    if (ws.config.get("under-reverse-proxy", false)) {
        install(ForwardedHeaderSupport)
        log.info(
            "Enabled Forwarded Header support (for reverse " +
                    "proxing)."
        )
    }
}