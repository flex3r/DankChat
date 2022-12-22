package com.flxrs.dankchat.data.repo.chat

data class ChatLoadingFailure(val step: ChatLoadingStep, val failure: Throwable)
