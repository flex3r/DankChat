package com.flxrs.dankchat.data.api

import androidx.annotation.Keep
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

open class ApiException(
    open val status: HttpStatusCode,
    open val url: Url?,
    override val message: String?,
    override val cause: Throwable? = null
) : Throwable(message, cause) {

    override fun toString(): String {
        return "ApiException(status=$status, url=$url, message=$message, cause=$cause)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApiException

        if (status != other.status) return false
        if (url != other.url) return false
        if (message != other.message) return false
        return cause == other.cause
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (cause?.hashCode() ?: 0)
        return result
    }
}

fun <R, T : R> Result<T>.recoverNotFoundWith(default: R): Result<R> = recoverCatching {
    when {
        it is ApiException && it.status == HttpStatusCode.NotFound -> default
        else                                                       -> throw it
    }
}

suspend fun HttpResponse.throwApiErrorOnFailure(json: Json): HttpResponse {
    if (status.isSuccess()) {
        return this
    }

    val errorBody = bodyAsText()
    val betterStatus = HttpStatusCode.fromValue(status.value)
    val errorMessage = json.decodeOrNull<GenericError>(errorBody)?.message ?: betterStatus.description

    throw ApiException(betterStatus, request.url, errorMessage)
}

@Keep
@Serializable
private data class GenericError(val message: String)
