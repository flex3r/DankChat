package com.flxrs.dankchat.data.api

import io.ktor.client.*
import io.ktor.client.request.*

class AuthApiService(private val ktorClient: HttpClient) {

    suspend fun validateUser(token: String) = ktorClient.get("validate") {
        bearerAuth(token)
    }
}