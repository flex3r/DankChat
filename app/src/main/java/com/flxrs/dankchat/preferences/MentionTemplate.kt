package com.flxrs.dankchat.preferences

enum class MentionTemplate(val value: String) {
	DEFAULT("$USER_VARIABLE "),
	WITH_AT("@$USER_VARIABLE "),
	WITH_AT_AND_COMMA("@$USER_VARIABLE, ")
}

const val USER_VARIABLE = "name"