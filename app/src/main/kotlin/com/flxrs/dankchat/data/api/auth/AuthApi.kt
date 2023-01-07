package com.flxrs.dankchat.data.api.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get

class AuthApi(private val ktorClient: HttpClient) {

    suspend fun validateUser(token: String) = ktorClient.get("validate") {
        bearerAuth(token)
    }
}