package me.devnatan.katan.webserver.util

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import me.devnatan.katan.webserver.KatanHTTPException

suspend fun PipelineContext<*, ApplicationCall>.respondOk(
    response: Any,
    status: HttpStatusCode = HttpStatusCode.OK,
) = call.respond(
    status, mapOf(
        "response" to "success",
        "data" to response
    )
)

suspend fun PipelineContext<*, ApplicationCall>.respondOk(
    vararg response: Pair<Any, Any>,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respondOk(response.toMap(), status)

fun respondError(
    errorCode: Int,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
): Nothing = throw KatanHTTPException(errorCode, status)