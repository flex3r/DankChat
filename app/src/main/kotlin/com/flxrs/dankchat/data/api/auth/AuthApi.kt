package com.flxrs.dankchat.data.api.auth

import io.ktor.client.*
import io.ktor.client.request.*

class AuthApi(private val ktorClient: HttpClient) {

    suspend fun validateUser(token: String) = ktorClient.get("validate") {
        bearerAuth(token)
    }
}